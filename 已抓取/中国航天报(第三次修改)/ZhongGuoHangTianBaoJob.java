package com.apabi.crawler.job.impl;

import java.net.URLEncoder;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.apabi.crawler.filter.FilterUtil;
import com.apabi.crawler.job.DefaultJob;
import com.apabi.crawler.model.Article;
import com.apabi.crawler.model.ArticleImage;
import com.apabi.crawler.model.Issue;
import com.apabi.crawler.model.JobConfig;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.CrawlerUtil;
import com.apabi.crawler.util.DebugControlCenter;
import com.apabi.crawler.util.HttpClientUtil;
import com.apabi.crawler.util.RegexUtil;

public class ZhongGuoHangTianBaoJob extends DefaultJob {
	
	private static Logger LOGGER = LoggerFactory.getLogger(ZhongGuoHangTianBaoJob.class);

	@Override
	public void parsePage() {
		Set<Page> pageSet = new HashSet<Page>();
		JobConfig jobConfig = issue.getJobConfig();
		String dateStr = DateFormatUtils.format(issue.getIssueDate(), jobConfig.getDatePattern());
		
		// 生成期次首页链接地址
		String issueIndexURL = issue.getIssueIndexURLTemplate().replaceAll("00000000", dateStr);
		
		String issueIndexResponseContent = HttpClientUtil.getResponseContent(issueIndexURL, issue.getJob());
		if(issueIndexResponseContent != null){
			Matcher dgIdMatcher = RegexUtil.matcher("var dgId = '(\\d+)';", issueIndexResponseContent);
			String dgId = "";
			while(dgIdMatcher.find()){
				dgId = dgIdMatcher.group(1);
			}
			
			//右侧板块内容解析
			String rightURL = issue.getJobConfig().getSiteRoot() + "forumsList.action?temp=dgId=" + dgId + "&date=" + dateStr;
			String rightContent = HttpClientUtil.getResponseContent(rightURL, issue.getJob());
			String rightRegex = "<h3><i>(\\d+)</i>&nbsp;([\\S\\s]+?)</h3>[\\S\\s]+?<a href=\"([\\S]+?)\">[\\S\\s]+?<a href=\"javascript:clickLists\\('(\\d+)','(\\d+)'\\)\">";
			Matcher rightMatcher = RegexUtil.matcher(rightRegex, rightContent);
			while(rightMatcher.find()){
				String pageName = rightMatcher.group(2);
				
				String pageNumber = rightMatcher.group(1);
				pageNumber = FilterUtil.pageNumberFilter(pageNumber);

				String pageIdP = rightMatcher.group(4);
				String dgIdP = rightMatcher.group(5);
				
				String pageURL = issue.getJobConfig().getSiteRoot() + "forums.action?dgId=" + dgIdP + "&pageId=" + pageIdP
				+ "&date=" + dateStr + "&products=11000112-1&rightList=" + dgId;
				
				Page page = new Page(pageName, pageNumber, pageURL, null, issue.getJob());
				page.setIssue(issue);
				
				// 指定版次号抓取
				DebugControlCenter.specifyPageNumberCrawl(page, pageSet);
			}
			issue.getPageQueue().addAll(pageSet);
			logParsePageSize(jobConfig.getPaperName(), issue.getIssueDate(), pageSet);
		}else{
			logIssueNotFound(issue);
		}
	}
	
	@Override
	public void parsePageArticle(Page page, String pageResponseContent) {
		//获取左侧内容
		Matcher dgIdMatcher = RegexUtil.matcher("var dgId = ['\"](\\d+)['\"];", pageResponseContent);
		dgIdMatcher.find();
		String dgId = dgIdMatcher.group(1);
		
		Matcher pageIdMatcher = RegexUtil.matcher("var pageId = ['\"](\\d+)['\"];", pageResponseContent);
		pageIdMatcher.find();
		String pageId = pageIdMatcher.group(1);
		
		Matcher rightListMatcher = RegexUtil.matcher("var rightList = ['\"](\\d+)['\"];", pageResponseContent);
		rightListMatcher.find();
		String rightList = rightListMatcher.group(1);
		
		String dateStr = DateFormatUtils.format(issue.getIssueDate(), jobConfig.getDatePattern());
		
		String leftURL = issue.getJobConfig().getSiteRoot() + "forumsArticle.action?temp=&dgId=" + dgId + "&pageId=" + pageId
		+ "&itemType=Title&date=" + dateStr + "&products=11000112-1&medianame=%E4%B8%AD%E5%9B%BD%E8%88%AA%E5%A4%A9%E6%8A%A5&rightList=" + rightList;
		String leftContent = HttpClientUtil.getResponseContent(leftURL, issue.getJob());
		
		if(leftContent != null){
			Set<Article> articleSet = new HashSet<Article>();
			Matcher matcher = RegexUtil.matcher("javascript:getArticle\\('(\\d+?)','(\\d+?)'\\);", leftContent);
			while(matcher.find()){
				String articleId = matcher.group(1);
				String jsId = matcher.group(2);
				String articleURL = issue.getJobConfig().getSiteRoot() + "article.action?dgId=" + dgId + "&pageId=" + pageId 
				+ "&articleId=" + articleId + "&date=" + dateStr + "&jsId=" + jsId + "&products=11000112-1&rightList=" + rightList;
				Set<double[]> articleCoordinateSet = new HashSet<double[]>();
				Article article = new Article(articleURL, articleCoordinateSet, page.getJob());
				
				// 合并两个相同URL稿件
				if (articleSet.contains(article)) {
					//article.combineArticleCoordinate(articleSet);
				} else {
					articleSet.add(article);
				}
				logParseArticle(page.getIssue().getJobConfig().getPaperName(), page.getIssue().getIssueDate(), page.getPageNumber(), page.getPageName(), articleURL);
			}
			page.setArticleSet(articleSet);
			logParseArticleSize(page.getIssue().getJobConfig().getPaperName(), page.getIssue().getIssueDate(), page.getPageNumber(), page.getPageName(), articleSet);
		}
	}
	
	private String getLeftContent(String pageResponseContent){
		Matcher dgIdMatcher = RegexUtil.matcher("var dgId = ['\"](\\d+)['\"];", pageResponseContent);
		dgIdMatcher.find();
		String dgId = dgIdMatcher.group(1);
		
		Matcher pageIdMatcher = RegexUtil.matcher("var pageId = ['\"](\\d+)['\"];", pageResponseContent);
		pageIdMatcher.find();
		String pageId = pageIdMatcher.group(1);
		
		Matcher rightListMatcher = RegexUtil.matcher("var rightList = ['\"](\\d+)['\"];", pageResponseContent);
		rightListMatcher.find();
		String rightList = rightListMatcher.group(1);
		
		String dateStr = DateFormatUtils.format(issue.getIssueDate(), jobConfig.getDatePattern());
		
		String leftURL = issue.getJobConfig().getSiteRoot() + "forumsArticle.action?temp=&dgId=" + dgId + "&pageId=" + pageId
		+ "&itemType=Title&date=" + dateStr + "&products=11000112-1&medianame=%E4%B8%AD%E5%9B%BD%E8%88%AA%E5%A4%A9%E6%8A%A5&rightList=" + rightList;
		String leftContent = HttpClientUtil.getResponseContent(leftURL, issue.getJob());
		return leftContent;
	}
	
	@Override
	public void parsePageImage(Page page, String pageResponseContent) {
		String leftContent = getLeftContent(pageResponseContent);
		String pageImageRegex = "url\\('([\\s\\S]+?)'\\)";
		Matcher pageImageMatcher = RegexUtil.matcher(pageImageRegex, leftContent);
		pageImageMatcher.find();
		String imageURL = pageImageMatcher.group(1);
		
		if (imageURL != null) {
			imageURL = issue.getJobConfig().getSiteRoot() + imageURL;
			LoggerFactory.getLogger(getClass()).debug(jobConfig.getPaperName() + CrawlerUtil.dateNormalFormat(issue.getIssueDate()) + "◆★" + page.getPageNumber() + "-" + page.getPageName() + "★◆版面图URL: " + imageURL);
			page.setPageImageURL(imageURL);
		}else{
			StringBuffer logBuffer = new StringBuffer();
			logBuffer.append(jobConfig.getPaperName()).append(CrawlerUtil.dateNormalFormat(issue.getIssueDate())).append("◆★");
			logBuffer.append(page.getPageNumber()).append("-").append(page.getPageName());
			logBuffer.append("★◆版面图pageImageRegex★链式匹配失败");
			LoggerFactory.getLogger(getClass()).warn(logBuffer.toString());
		}
	}
	
	@Override
	public void parseArticle(Page page) {
		JobConfig jobConfig = page.getIssue().getJobConfig();
		
		if (page.getArticleSet() != null) {
			Iterator<Article> articleIterator = page.getArticleSet().iterator();
			while (articleIterator.hasNext()) {
				Article article = articleIterator.next();
				final String articleContent = HttpClientUtil.getResponseContent(article.getArticleURL(), page.getJob());
				if (articleContent != null) {
					Matcher dgIdMatcher = RegexUtil.matcher("var dgId = ['\"](\\d+)['\"];", articleContent);
					dgIdMatcher.find();
					String dgId = dgIdMatcher.group(1);
					
					Matcher pageIdMatcher = RegexUtil.matcher("var pageId = ['\"](\\d+)['\"];", articleContent);
					pageIdMatcher.find();
					String pageId = pageIdMatcher.group(1);
					
					Matcher dateMatcher = RegexUtil.matcher("var date = ['\"](\\d+)['\"];", articleContent);
					dateMatcher.find();
					String date = dateMatcher.group(1);
					
					Matcher articleIdMatcher = RegexUtil.matcher("var articleId = ['\"](\\d+)['\"];", articleContent);
					articleIdMatcher.find();
					String articleId = articleIdMatcher.group(1);
					
					Matcher jsIdMatcher = RegexUtil.matcher("var jsId = ['\"](\\d+)['\"];", articleContent);
					jsIdMatcher.find();
					String jsId = jsIdMatcher.group(1);
					
					String rightURL = issue.getJobConfig().getSiteRoot() + "articleContent.action?dgId="+dgId+"&pageId="+pageId+"&date="+date+"&articleId="+articleId+"&jsId="+jsId+"&products=11000112-1";
					final String rightContent = HttpClientUtil.getResponseContent(rightURL, page.getJob());
					
					if(rightContent != null){
						String title = "";
						Matcher titleMatcher = RegexUtil.matcher("标题 : <a[\\s\\S]*?>([\\s\\S]*?)</a>", rightContent);
						if(titleMatcher.find()){
							title = titleMatcher.group(1);
							title = FilterUtil.trimFilter(title);
						}else{
							LOGGER.error(jobConfig.getPaperName() + "★稿件title★链式匹配失败");
						}
						
						String author = "";
						Matcher authorMatcher = RegexUtil.matcher("作者 : <a[\\s\\S]*?>([\\s\\S]*?)</a>", rightContent);
						if(authorMatcher.find()){
							author = authorMatcher.group(1);
							author = FilterUtil.trimFilter(author);
						}else{
							LOGGER.error(jobConfig.getPaperName() + "★稿件author★链式匹配失败");
						}
						
						String content = "";
						Matcher contentMatcher = RegexUtil.matcher("<div class=\"tezheng\"[\\s\\S]+?>([\\s\\S]+?)</div>", rightContent);
						if(contentMatcher.find()){
							content = contentMatcher.group(1);
							content = FilterUtil.trimFilter(content);
						}else{
							LOGGER.error(jobConfig.getPaperName() + "★稿件content★链式匹配失败");
						}
						
						Set<ArticleImage> articleImageSet = new HashSet<ArticleImage>();
						Matcher imageMatcher = RegexUtil.matcher("<p[\\s\\S]+?<img src=\"([\\s\\S]+?)\">[\\s\\S]+?</p>", rightContent);
						while(imageMatcher.find()){
							String imageURL = imageMatcher.group(1);
							imageURL = issue.getJobConfig().getSiteRoot() + imageURL;
							ArticleImage articleImage = new ArticleImage(imageURL, null, article.getJob());
							articleImageSet.add(articleImage);
							logParseArticleImageSize(jobConfig.getPaperName(), page.getIssue().getIssueDate(), page.getPageNumber(), page.getPageName(), articleImageSet);
						}
						
						article.extend(author, null, null, title, null, content, articleImageSet);
					}
				} else {
					articleIterator.remove();
				}
			}
		}
	}
	
	public static void logParseIssuePageFailure(Issue issue) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("期次◆★");
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●").append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆版次pageRegex★链式匹配失败");
		LOGGER.warn(logBuffer.toString());
	}
	public static void logParsePageSize(String paperName, Date issueDate, Set<Page> pageSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("解析出◆★").append(paperName).append("●").append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("★◆版面数: ").append(pageSet.size());
		LOGGER.info(logBuffer.toString());
	}
	public static void logParseArticle(String paperName, Date issueDate, String pageNumber, String pageName, String articleURL) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append(", 解析出◆★").append(pageNumber).append("-").append(pageName).append("★◆, 稿件URL: ").append(articleURL);
		LOGGER.debug(logBuffer.toString());
	}
	public static void logParseArticleSize(String paperName, Date issueDate, String pageNumber, String pageName, Set<Article> articleSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append(", 解析出◆★").append(pageNumber).append("-").append(pageName).append("★◆稿件数: ").append(articleSet.size());
		LOGGER.debug(logBuffer.toString());
	}
	public static void logIssueNotFound(Issue issue) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("期次◆★");
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●").append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆不存在, 期次首页: ");
		if(StringUtils.isNotBlank(issue.getIssueIndexURLTemplate())){
			logBuffer.append(issue.getIssueIndexURLTemplate().replaceAll(issue.getJobConfig().getDateRegex(), DateFormatUtils.format(issue.getIssueDate(), issue.getJobConfig().getDatePattern())));
		}
		LOGGER.info(logBuffer.toString());
	}
	public static void logParseArticleImageSize(String paperName, Date issueDate, String pageNumber, String pageName, Set<ArticleImage> articleImageSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("解析出◆★").append(pageNumber).append("-").append(pageName);
		logBuffer.append("★◆图片数: ").append(articleImageSet.size());
		LOGGER.debug(logBuffer.toString());
	}
	
	public static void main(String[] args) throws Exception{
		System.out.println(URLEncoder.encode("中国航天报", "UTF-8"));
	}
	
}
