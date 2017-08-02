package com.apabi.crawler.job.impl;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.apabi.crawler.job.DefaultJob;
import com.apabi.crawler.model.Article;
import com.apabi.crawler.model.GlobalJobConfig;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.CrawlChain;
import com.apabi.crawler.util.CrawlerUtil;
import com.apabi.crawler.util.ParsePageArticleUtil;
import com.apabi.crawler.util.ParsePageUtil;
import com.apabi.crawler.util.RegexUtil;

public class ChangShaWanBao extends DefaultJob {
	private static final Logger LOGGER = LoggerFactory.getLogger(ParsePageArticleUtil.class);
	
	@Override
	public void parsePage() {
		ParsePageUtil.parsePage(issue, 1, 3, 2);
		
	}
	
	@Override
	public void parsePageArticle(Page page, String pageResponseContent) {
		if (page.getPagePDFURL() == null) {
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
			String pattern = "<div id=\"div_bt_nav_1\"[\\s\\S]*?<\\/div>";
			
			Pattern r = Pattern.compile(pattern);
			Matcher m = r.matcher(str);
			String div=null;
			if(m.find()){
				div=m.group(0);
			}
			
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
