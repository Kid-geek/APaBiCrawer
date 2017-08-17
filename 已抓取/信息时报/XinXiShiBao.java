package com.apabi.crawler.job.impl;

import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.apabi.crawler.filter.FilterUtil;
import com.apabi.crawler.job.Job;
import com.apabi.crawler.job.ParsePageURLNumberNameJob;
import com.apabi.crawler.model.Article;
import com.apabi.crawler.model.ArticleImage;
import com.apabi.crawler.model.GlobalJobConfig;
import com.apabi.crawler.model.JobConfig;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.CrawlChain;
import com.apabi.crawler.util.CrawlerUtil;
import com.apabi.crawler.util.HttpClientUtil;
import com.apabi.crawler.util.RegexUtil;



/**
 * @author 
 *	信息时报
 *	用getArticleImageSet转换一下图片链接
 *
 */
public class XinXiShiBao extends ParsePageURLNumberNameJob {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(XinXiShiBao.class);

	/** 
	 * 解析稿件图片集
	 * String imageURL = matcher.group(1);
	 * String imageDescription = matcher.group(2);
	 */
	public static Set<ArticleImage> getArticleImageSet(String regex, CharSequence input, String fromURL, Job job) {
		Set<ArticleImage> articleImageSet = new HashSet<ArticleImage>();
		Matcher matcher = RegexUtil.matcher(regex, input);
		int groupCount = matcher.groupCount();
		while (matcher.find()) {
			String imageURL = matcher.group(1);
			imageURL = CrawlerUtil.getAbsoluteURL(fromURL, imageURL);
			imageURL = imageURL.replace("/html", ":8000");
			String imageDescription = null;
			if (groupCount == 2) {
				imageDescription = matcher.group(2);
			}
			ArticleImage articleImage = new ArticleImage(imageURL, imageDescription, job);
			articleImageSet.add(articleImage);
		}
		return articleImageSet;
	}
	
	
	@Override
	public void parseArticle(Page page) {

		JobConfig jobConfig = page.getIssue().getJobConfig();

		if (page.getArticleSet() != null) {

			Iterator<Article> articleIterator = page.getArticleSet().iterator();
			while (articleIterator.hasNext()) {
				Article article = articleIterator.next();
				final String articleResponseContent = HttpClientUtil.getResponseContent(article.getArticleURL(),
						page.getJob());
				if (articleResponseContent != null) {

					String articleIntrotitleRegex = CrawlChain.getRegex(jobConfig.getArticleIntrotitleRegex(),
							GlobalJobConfig.getInstance().getArticleIntrotitleRegexList(), articleResponseContent,
							jobConfig.getPaperName() + "★稿件articleIntrotitleRegex★链式匹配失败");
					String articleIntrotitle = null;
					if (articleIntrotitleRegex != null) {
						articleIntrotitle = RegexUtil.getGroup1MatchContent(articleIntrotitleRegex,
								articleResponseContent);
						articleIntrotitle = FilterUtil.trimFilter(articleIntrotitle);
					}

					String articleTitleRegex = CrawlChain.getRegex(jobConfig.getArticleTitleRegex(),
							GlobalJobConfig.getInstance().getArticleTitleRegexList(), articleResponseContent,
							jobConfig.getPaperName() + "★稿件articleTitleRegex★链式匹配失败");
					String articleTitle = null;
					if (articleTitleRegex != null) {
						articleTitle = RegexUtil.getGroup1MatchContent(articleTitleRegex, articleResponseContent);
						articleTitle = FilterUtil.trimFilter(articleTitle);
					}
					logParseArticle(jobConfig.getPaperName(), page.getIssue().getIssueDate(), page.getPageNumber(),
							page.getPageName(), articleTitle);

					String articleSubTitleRegex = CrawlChain.getRegex(jobConfig.getArticleSubTitleRegex(),
							GlobalJobConfig.getInstance().getArticleSubTitleRegexList(), articleResponseContent,
							jobConfig.getPaperName() + "★稿件articleSubTitleRegex★链式匹配失败");
					String articleSubTitle = null;
					if (articleSubTitleRegex != null) {
						articleSubTitle = RegexUtil.getGroup1MatchContent(articleSubTitleRegex, articleResponseContent);
						articleSubTitle = FilterUtil.trimFilter(articleSubTitle);
					}

					String articleAuthorRegex = CrawlChain.getRegex(jobConfig.getArticleAuthorRegex(),
							GlobalJobConfig.getInstance().getArticleAuthorRegexList(), articleResponseContent,
							jobConfig.getPaperName() + "★稿件articleAuthorRegex★链式匹配失败");
					String articleAuthor = null;
					if (articleAuthorRegex != null) {
						articleAuthor = RegexUtil.getGroup1MatchContent(articleAuthorRegex, articleResponseContent);
						articleAuthor = FilterUtil.trimFilter(articleAuthor);
					}

					String articleSourceRegex = CrawlChain.getRegex(jobConfig.getArticleSourceRegex(),
							GlobalJobConfig.getInstance().getArticleSourceRegexList(), articleResponseContent,
							jobConfig.getPaperName() + "★稿件articleSourceRegex★链式匹配失败");
					String articleSource = null;
					if (articleSourceRegex != null) {
						articleSource = RegexUtil.getGroup1MatchContent(articleSourceRegex, articleResponseContent);
						articleSource = FilterUtil.trimFilter(articleSource);
					}

					String articleContentRegex = CrawlChain.getRegex(jobConfig.getArticleContentRegex(),
							GlobalJobConfig.getInstance().getArticleContentRegexList(), articleResponseContent,
							jobConfig.getPaperName() + "★稿件articleContentRegex★链式匹配失败");
					String articleContent = null;
					if (articleContentRegex != null) {
						articleContent = RegexUtil.getGroup1MatchContent(articleContentRegex, articleResponseContent);
						articleContent = FilterUtil.trimFilter(articleContent);
					}

					String articleImageRegex = CrawlChain.getRegex(jobConfig.getArticleImageRegex(),
							GlobalJobConfig.getInstance().getArticleImageRegexList(), articleResponseContent,
							jobConfig.getPaperName() + "★稿件articleImageRegex★链式匹配失败");
					Set<ArticleImage> articleImageSet = null;
					if (articleImageRegex != null) {
						articleImageSet = getArticleImageSet(articleImageRegex, articleResponseContent,
								article.getArticleURL(), article.getJob());
						logParseArticleImageSize(jobConfig.getPaperName(), page.getIssue().getIssueDate(),
								page.getPageNumber(), page.getPageName(), articleImageSet);
					}

					article.extend(articleAuthor, articleSource, articleIntrotitle, articleTitle, articleSubTitle,
							articleContent, articleImageSet);
				} else {
					articleIterator.remove();
				}
			}
		}
	}
	
	public static void logParseArticle(String paperName, Date issueDate, String pageNumber, String pageName, String articleTitle) {
		StringBuffer logBuffer = new StringBuffer();
		
		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("解析出◆★").append(pageNumber).append("-").append(pageName);
		logBuffer.append("★◆稿件标题: ").append(articleTitle);
		LOGGER.debug(logBuffer.toString());
	}
	public static void logParseArticleImageSize(String paperName, Date issueDate, String pageNumber, String pageName, Set<ArticleImage> articleImageSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("解析出◆★").append(pageNumber).append("-").append(pageName);
		logBuffer.append("★◆图片数: ").append(articleImageSet.size());
		LOGGER.debug(logBuffer.toString());
	}
	
	
}
