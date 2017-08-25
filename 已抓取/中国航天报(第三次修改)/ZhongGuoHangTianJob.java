package com.apabi.crawler.job.impl;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.apabi.crawler.filter.FilterUtil;
import com.apabi.crawler.job.Job;
import com.apabi.crawler.job.ParsePageURLNumberNameJob;
import com.apabi.crawler.model.Article;
import com.apabi.crawler.model.ArticleImage;
import com.apabi.crawler.model.GlobalJobConfig;
import com.apabi.crawler.model.Issue;
import com.apabi.crawler.model.JobConfig;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.CrawlChain;
import com.apabi.crawler.util.CrawlerUtil;
import com.apabi.crawler.util.DebugControlCenter;
import com.apabi.crawler.util.HttpClientUtil;
import com.apabi.crawler.util.RegexUtil;

/**
 * @author zh
 * 先解析出pageid dgid
 * 抓包获取版次列表链接规则  拼接链接  获取版次
 * 抓包获取文章列表链接规则  拼接链接  获取文章
 * 抓包获取文章页链接规则  拼接规则  正则匹配想要的内容
 *
 */
public class ZhongGuoHangTianJob extends ParsePageURLNumberNameJob {
	private static Logger LOGGER = LoggerFactory.getLogger(ZhongGuoHangTianJob.class);

	@Override
	public void parsePage() {
		String rootURL = "http://124.205.131.137:8088/";
		Set<Page> pageSet = new HashSet<Page>();
		JobConfig jobConfig = issue.getJobConfig();
		// 期次地址
		String issueIndexURL = issue.getIssueIndexURLTemplate().replaceAll(jobConfig.getDateRegex(),
				DateFormatUtils.format(issue.getIssueDate(), jobConfig.getDatePattern()));
		// 日期
		String data = DateFormatUtils.format(issue.getIssueDate(), "yyyyMMdd");
		String issueIndexResponseContent = HttpClientUtil.getResponseContent(issueIndexURL, issue.getJob());
		if (StringUtils.isNotBlank(issueIndexResponseContent)) {
			String dqid = "";
			Pattern dqidPattern = Pattern.compile(
					"<input type=\"hidden\" value=\"(.*?)\" id=\"dgId\" />[\\s\\S]*?<input type=\"hidden\" value=\"(.*?)\" id=\"pageId\" />");
			Matcher dqidMatcher = dqidPattern.matcher(issueIndexResponseContent);
			while (dqidMatcher.find()) {
				dqid = dqidMatcher.group(1);
			}
			String listURL = "http://124.205.131.137:8088/forumsList.action?&dgId=" + dqid + "&date=" + data;
			System.out.println("-----" + listURL);

			String listResponseContent = getURLContent(listURL);

			Pattern pageidPattern = Pattern.compile(
					"<h3><i>(.*?)</i>&nbsp; (.*?)</h3>[\\s\\S]*?<a href=\"(getpdf.action?[\\s\\S]*?)\">[\\s\\S]*?<a href=\"javascript:clickLists\\('([\\s\\S]*?)',");
			Matcher pageidMatcher = pageidPattern.matcher(listResponseContent);
			while (pageidMatcher.find()) {
				String pageNumber = pageidMatcher.group(1);
				String pageName = pageidMatcher.group(2);
				String pageid = pageidMatcher.group(4);
				String pageURL = "http://124.205.131.137:8088/forums.action?dgId=" + dqid + "&pageId=" + pageid
						+ "&date=" + data + "&products=11000112-1&rightList=" + dqid;
				Page page = new Page(pageName, pageNumber, pageURL, issue.getJob());
				page.setIssue(issue);
				// 指定版次号抓取
				DebugControlCenter.specifyPageNumberCrawl(page, pageSet);
			}
			issue.getPageQueue().addAll(pageSet);
			logParsePageSize(jobConfig.getPaperName(), issue.getIssueDate(), pageSet);

		} else {
			logParseIssuePageFailure(issue);
		}

	}

	@Override
	public void parsePageImage(Page page, String pageResponseContent) {
		Pattern idPattern = Pattern.compile("dgId=([\\s\\S]*?)&pageId=([\\s\\S]*?)&");
		Matcher idMatcher = idPattern.matcher(page.getPageURL());
		String dgId = null;
		String pageId = null;
		while (idMatcher.find()) {
			dgId = idMatcher.group(1);
			pageId = idMatcher.group(2);
		}
		String pageImageURL = "http://124.205.131.137:8088/page4pic.action?width=400&height=593&pageId=" + pageId
				+ "&dgId=" + dgId;
		pageImageURL = CrawlerUtil.getAbsoluteURL(page.getPageURL(), pageImageURL);
		LoggerFactory.getLogger(getClass())
				.debug(jobConfig.getPaperName() + CrawlerUtil.dateNormalFormat(issue.getIssueDate()) + "◆★"
						+ page.getPageNumber() + "-" + page.getPageName() + "★◆版面图URL: " + pageImageURL);
		page.setPageImageURL(pageImageURL);
	}

	@Override
	public void parsePageArticle(Page page, String pageResponseContent) {
		Pattern idPattern = Pattern.compile("dgId=([\\s\\S]*?)&pageId=([\\s\\S]*?)&");
		Matcher idMatcher = idPattern.matcher(page.getPageURL());
		String data = DateFormatUtils.format(issue.getIssueDate(), "yyyyMMdd");
		String dgId = null;
		String pageId = null;
		while (idMatcher.find()) {
			dgId = idMatcher.group(1);
			pageId = idMatcher.group(2);
		}

		String articleListURL = "http://124.205.131.137:8088/forumsTitles.action?&dgId=" + dgId + "&pageId=" + pageId
				+ "&itemType=Title&date=" + data;

		String articleListContent = getURLContent(articleListURL);

		String articleId = null;
		String jsId = null;

		Set<Article> articleSet = new LinkedHashSet<Article>();
		Pattern articlePattern = Pattern.compile("href=\"javascript:getArticle\\('([\\s\\S]*?)','([\\s\\S]*?)'\\);\">");
		Matcher articleMatcher = articlePattern.matcher(articleListContent);
		while (articleMatcher.find()) {
			articleId = articleMatcher.group(1);
			jsId = articleMatcher.group(2);

			String articleURL = "http://124.205.131.137:8088/article.action?dgId=" + dgId + "&pageId=" + pageId
					+ "&articleId=" + articleId + "&date=" + data + "&jsId=" + jsId + "&products=11000112-1&rightList="
					+ dgId;
			articleURL = CrawlerUtil.getAbsoluteURL(page.getPageURL(), articleURL);
			Article article = new Article(articleURL, page.getJob());
			// 合并两个相同URL稿件的coordinate
			if (articleSet.contains(article)) {
				article.combineArticleCoordinate(articleSet);
			} else {
				articleSet.add(article);
			}
			logParseArticle(page.getIssue().getJobConfig().getPaperName(), page.getIssue().getIssueDate(),
					page.getPageNumber(), page.getPageName(), articleURL);
		}

		page.setArticleSet(articleSet);
		logParseArticleSize(page.getIssue().getJobConfig().getPaperName(), page.getIssue().getIssueDate(),
				page.getPageNumber(), page.getPageName(), articleSet);
	}

	/** 解析稿件详情 */
	@Override
	public void parseArticle(Page page) {
		JobConfig jobConfig = page.getIssue().getJobConfig();
		if (page.getArticleSet() != null) {
			Iterator<Article> articleIterator = page.getArticleSet().iterator();
			while (articleIterator.hasNext()) {
				Article article = articleIterator.next();
				String articleURL = article.getArticleURL();
				Pattern articleIndexURLPattern = Pattern
						.compile("dgId=([\\s\\S]*?)&pageId=([\\s\\S]*?)&articleId=([\\s\\S]*?)&jsId=([\\s\\S]*?)&");
				Matcher articleIndexURLMatcher = articleIndexURLPattern.matcher(articleURL);
				String data = DateFormatUtils.format(issue.getIssueDate(), "yyyyMMdd");
				String dgId = null;
				String pageId = null;
				String articleId = null;
				String jsId = null;
				while (articleIndexURLMatcher.find()) {
					dgId = articleIndexURLMatcher.group(1);
					pageId = articleIndexURLMatcher.group(2);
					articleId = articleIndexURLMatcher.group(3);
					jsId = articleIndexURLMatcher.group(4);
				}
				String articleIndexURL = "http://124.205.131.137:8088/articleContent.action?&dgId=" + dgId + "&pageId="
						+ pageId + "&date=" + data + "&articleId=" + articleId + "&jsId=" + jsId
						+ "&products=11000112-1";
				articleURL = articleIndexURL;
				String articleResponseContent = getURLContent(articleIndexURL);
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
						articleImageSet = getArticleImageSet(articleImageRegex, articleResponseContent, articleIndexURL,
								article.getJob());
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

	public static Set<ArticleImage> getArticleImageSet(String regex, CharSequence input, String fromURL, Job job) {
		Set<ArticleImage> articleImageSet = new HashSet<ArticleImage>();
		Matcher matcher = RegexUtil.matcher(regex, input);
		int groupCount = matcher.groupCount();
		while (matcher.find()) {
			String imageURL = matcher.group(1);
			imageURL = CrawlerUtil.getAbsoluteURL(fromURL, imageURL);
			String imageDescription = null;
			if (groupCount == 2) {
				imageDescription = matcher.group(2);
			}
			ArticleImage articleImage = new ArticleImage(imageURL, imageDescription, job);
			articleImageSet.add(articleImage);
		}
		return articleImageSet;
	}

	// 获取网页源码
	public String getURLContent(String URL) {
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet httpget = new HttpGet(URL);
		String result = null;
		try {
			CloseableHttpResponse response = httpclient.execute(httpget);
			result = EntityUtils.toString(response.getEntity(), "utf-8");
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	public static void logIssueNotFound(Issue issue) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("期次◆★");
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●")
				.append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆不存在, 期次首页: ");
		if (StringUtils.isNotBlank(issue.getIssueIndexURLTemplate())) {
			logBuffer.append(issue.getIssueIndexURLTemplate().replaceAll(issue.getJobConfig().getDateRegex(),
					DateFormatUtils.format(issue.getIssueDate(), issue.getJobConfig().getDatePattern())));
		}
		LOGGER.info(logBuffer.toString());
	}

	public static void logParseIssuePageFailure(Issue issue) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("期次◆★");
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●")
				.append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆版次pageRegex★链式匹配失败");
		LOGGER.warn(logBuffer.toString());
	}

	public static void logParseIssueFolderFailure(Issue issue) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("期次◆★");
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●")
				.append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆叠folderURLRegex★匹配失败");
		LOGGER.warn(logBuffer.toString());
	}

	public static void logIssueFolderNotFound(Issue issue, String folderURL) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("期次◆★");
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●")
				.append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆叠不存在, 叠URL: ");
		logBuffer.append(folderURL);
		LOGGER.warn(logBuffer.toString());
	}

	public static void logParsePageSuccess(Page page) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append(page.getIssue().getJobConfig().getPaperName())
				.append(CrawlerUtil.dateNormalFormat(page.getIssue().getIssueDate()));
		logBuffer.append(", 解析出版面◆★").append(page.getPageNumber()).append("-").append(page.getPageName());
		logBuffer.append("★◆, URL: ").append(page.getPageURL());
		LOGGER.debug(logBuffer.toString());
	}

	public static void logSpecifyPageNumberCrawl(Page page) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append(page.getIssue().getJobConfig().getPaperName())
				.append(CrawlerUtil.dateNormalFormat(page.getIssue().getIssueDate()));
		logBuffer.append(", 指定版次抓取◆★").append(page.getPageNumber()).append("-").append(page.getPageName());
		logBuffer.append("★◆, URL: ").append(page.getPageURL());
		LOGGER.debug(logBuffer.toString());
	}

	public static void logParsePageSize(String paperName, Date issueDate, Set<Page> pageSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("解析出◆★").append(paperName).append("●").append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("★◆版面数: ").append(pageSet.size());
		LOGGER.info(logBuffer.toString());
	}

	public static void logParseFolderSize(String paperName, Date issueDate, Set<String> folderURLSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("解析出◆★").append(paperName).append("●").append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("★◆叠数: ").append(folderURLSet.size());
		LOGGER.debug(logBuffer.toString());
	}

	public static void logParseArticle(String paperName, Date issueDate, String pageNumber, String pageName,
			String articleTitle) {
		StringBuffer logBuffer = new StringBuffer();

		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("解析出◆★").append(pageNumber).append("-").append(pageName);
		logBuffer.append("★◆稿件标题: ").append(articleTitle);
		LOGGER.debug(logBuffer.toString());
	}

	public static void logParseArticleSize(String paperName, Date issueDate, String pageNumber, String pageName,
			Set<Article> articleSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append(", 解析出◆★").append(pageNumber).append("-").append(pageName).append("★◆稿件数: ")
				.append(articleSet.size());
		LOGGER.debug(logBuffer.toString());
	}

	public static void logParseArticleImageSize(String paperName, Date issueDate, String pageNumber, String pageName,
			Set<ArticleImage> articleImageSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("解析出◆★").append(pageNumber).append("-").append(pageName);
		logBuffer.append("★◆图片数: ").append(articleImageSet.size());
		LOGGER.debug(logBuffer.toString());
	}

}
