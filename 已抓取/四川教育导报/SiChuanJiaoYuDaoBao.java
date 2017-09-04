package com.apabi.crawler.job.impl;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.apabi.crawler.filter.FilterUtil;
import com.apabi.crawler.job.DefaultJob;
import com.apabi.crawler.model.Article;
import com.apabi.crawler.model.GlobalJobConfig;
import com.apabi.crawler.model.Issue;
import com.apabi.crawler.model.JobConfig;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.CrawlChain;
import com.apabi.crawler.util.CrawlerUtil;
import com.apabi.crawler.util.DebugControlCenter;
import com.apabi.crawler.util.HttpClientUtil;
import com.apabi.crawler.util.ParsePageArticleUtil;
import com.apabi.crawler.util.RegexUtil;

public class SiChuanJiaoYuDaoBao extends DefaultJob{
	private static Logger LOGGER = LoggerFactory.getLogger(SiChuanJiaoYuDaoBao.class);

	@Override
	public void parsePage() {
		int pagePDFURLIndex=1 , pageURLIndex = 2, pageNumberIndex = 3, pageNameIndex = 4;
		Set<Page> pageSet = new HashSet<Page>();
		JobConfig jobConfig = issue.getJobConfig();
		String issueIndexURL = "";
		// 获取日期
		Date issueDate = issue.getIssueDate();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(issueDate);

		// 格式化日期为00000000
		SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd");
		String date = dateFormat.format(calendar.getTime());
		issueIndexURL = "http://jydb.scedumedia.com/DocumentElectronic/"+getURL(date);
		String issueIndexResponseContent = HttpClientUtil.getResponseContent(issueIndexURL, issue.getJob());
		if (StringUtils.isNotBlank(issueIndexResponseContent)) {
			
			int groupCount = 3;
			if (pagePDFURLIndex != 0) {
				groupCount = 4;
			}
			String pageRegex = CrawlChain.getRegex(jobConfig.getPageRegex(), GlobalJobConfig.getInstance().getPageRegexList(),
														groupCount,	issueIndexResponseContent);
			
			if (pageRegex != null) {
				
				String str = issueIndexResponseContent;
				String pattern = "<div class=\"list_content catalog\">[\\s\\S]*?<div class=\"right_box\">";
				
				Pattern r = Pattern.compile(pattern);
				Matcher m = r.matcher(str);
				String div=null;
				if(m.find()){
					div=m.group(0);
				}
				
				Matcher matcher = RegexUtil.matcher(pageRegex, div);
				pageSet = new HashSet<Page>();
				while (matcher.find()) {
					String pageName = matcher.group(pageNameIndex);
					String pageNumber = matcher.group(pageNumberIndex);
					pageNumber = FilterUtil.pageNumberFilter(pageNumber);
					String pageURL = matcher.group(pageURLIndex);
					pageURL = CrawlerUtil.getAbsoluteURL(issueIndexURL, pageURL);
					String pagePDFURL = null;
					if (pagePDFURLIndex != 0) {
						pagePDFURL = matcher.group(pagePDFURLIndex);
						pagePDFURL = CrawlerUtil.getAbsoluteURL(issueIndexURL, pagePDFURL);
					}
					
					Page page = new Page(pageName, pageNumber, pageURL, pagePDFURL, issue.getJob());
					page.setIssue(issue);
					
					// 指定版次号抓取
					DebugControlCenter.specifyPageNumberCrawl(page, pageSet);
				}
				issue.getPageQueue().addAll(pageSet);
				logParsePageSize(jobConfig.getPaperName(), issue.getIssueDate(), pageSet);
			} 
		}else {
			logParseIssuePageFailure(issue);
		} 
		
	}


	@Override
	public void parsePageArticle(Page page, String pageResponseContent) {
		if(page.getPagePDFURL() == null) {
			String pagePDFRegex = CrawlChain.getRegex(
													page.getIssue().getJobConfig().getPagePDFRegex(), 
													GlobalJobConfig.getInstance().getPagePDFRegexList(),
													pageResponseContent,
													page.getIssue().getJobConfig().getPaperName() + "★版面PDFpagePDFRegex★链式匹配失败");
			
			if (pagePDFRegex != null) {
				String pagePDFURL = RegexUtil.getGroup1MatchContent(pagePDFRegex, pageResponseContent);
				pagePDFURL = CrawlerUtil.getAbsoluteURL(page.getPageURL(), pagePDFURL);
				LOGGER.debug(page.getIssue().getJobConfig().getPaperName() 
								+ CrawlerUtil.dateNormalFormat(page.getIssue().getIssueDate()) 
									+ "◆★" + page.getPageNumber() + "-" + page.getPageName() + "★◆版面PDFURL: " + pagePDFURL);
				page.setPagePDFURL(pagePDFURL);
			}
		}
		
		String articleRegex = CrawlChain.getRegex(
												page.getIssue().getJobConfig().getArticleRegex(), 
												GlobalJobConfig.getInstance().getArticleRegexList(),
												2, pageResponseContent,
												page.getIssue().getJobConfig().getPaperName() + "★版面articleRegex★链式匹配失败");
		if (articleRegex != null) {
			Set<Article> articleSet = new HashSet<Article>();
			
			String str = pageResponseContent;
			String pattern = "<div class=\"list_content dotnews\">[\\s\\S]*?<div class=\"listbox\"";
			
			Pattern r = Pattern.compile(pattern);
			Matcher m = r.matcher(str);
			String div=null;
			if(m.find()){
				div=m.group(0);
			}
//			
			Matcher matcher = RegexUtil.matcher(articleRegex, div);
			while (matcher.find()) {
				String articleURL = matcher.group(1);
				// 去除类似content_75574.htm?div=-1, 后面的?div=-1
				if (articleURL.contains("?")) {
					articleURL = articleURL.substring(0, articleURL.lastIndexOf("?"));
				}
				articleURL = CrawlerUtil.getAbsoluteURL(page.getPageURL(), articleURL);
				
				Article article = new Article(articleURL, page.getJob());
				
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
	
	
	
	public static String getURL(String date) {
		String RootURL="http://jydb.scedumedia.com/DocumentElectronic/index.html?ReturnUrl=%2f";
		CloseableHttpClient client = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet(RootURL);
		CloseableHttpResponse response;
		String pageURL="";
		try {
			response = client.execute(httpGet);
			HttpEntity entity = response.getEntity();
			InputStream in = entity.getContent();
			String result = IOUtils.toString(in);
			Pattern pattern=Pattern.compile(date+"</span> <a[\\s\\S]*? href=\"([\\s\\S]*?.html)\" target=\"_blank\">[\\s\\S]*?2017年");
			Matcher matcher=pattern.matcher(result);
			while(matcher.find()){
				pageURL=matcher.group(1);
			}
			
		} catch (ClientProtocolException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		} catch (IOException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		}
		return pageURL;
	}
	
	public static void logParsePageSize(String paperName, Date issueDate, Set<Page> pageSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("解析出◆★").append(paperName).append("●").append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("★◆版面数: ").append(pageSet.size());
		LOGGER.info(logBuffer.toString());
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
	public static void logParseIssuePageFailure(Issue issue) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("期次◆★");
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●").append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆版次pageRegex★链式匹配失败");
		LOGGER.warn(logBuffer.toString());
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
	
}
