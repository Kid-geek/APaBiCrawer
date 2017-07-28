/**
 * 
 */
package com.apabi.crawler.job.impl;
import java.util.regex.Matcher;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.apabi.crawler.job.DefaultJob;
import com.apabi.crawler.model.Article;
import com.apabi.crawler.model.Issue;
import com.apabi.crawler.model.JobConfig;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.CrawlerUtil;
import com.apabi.crawler.util.DebugControlCenter;
import com.apabi.crawler.util.HttpClientUtil;
import com.apabi.crawler.util.RegexUtil;

/**
 * @author Zh
 * iframe
 * 杭州日报1&都市快報2&每日商报3
 *
 */
public class DuShiKuaiBao extends DefaultJob{
	private static Logger LOGGER=LoggerFactory.getLogger(DuShiKuaiBao.class);
	String flag;
	@Override
	public  void parsePage(){
		Set<Page> pageSet=new HashSet<Page>();
		JobConfig jobConfig=issue.getJobConfig();
//		String indexUrl=jobConfig.getSiteRoot()+jobConfig.getIndexURLRegex();
//		String dateStr=DateFormatUtils.format(issue.getIssueDate(), jobConfig.getDatePattern());
		String indexURL1 = issue.getIssueIndexURLTemplate().replaceAll(jobConfig.getDateRegex(), DateFormatUtils.format(issue.getIssueDate(), jobConfig.getDatePattern()));
		String indexURL = indexURL1.replaceAll("\\d{8}", DateFormatUtils.format(issue.getIssueDate(),"yyyyMMdd"));
		String responseContent= HttpClientUtil.getResponseContent(indexURL, "UTF-8");
//		System.out.println(indexURL);
		
		if(responseContent!=null){
			Matcher bmmatcher=RegexUtil.matcher(jobConfig.getPageRegex(),responseContent);
			while(bmmatcher.find()){
				String pageNum=bmmatcher.group(1);
				String pageName=bmmatcher.group(2);
				
				if(jobConfig.getPaperName().equals("杭州日报")){
					
					flag="1";
				}
				else if(jobConfig.getPaperName().equals("都市快报")){
					flag="2";
				}
				else if(jobConfig.getPaperName().equals("每日商报")){
					flag="3";
				}
				System.out.println("----------------------------------"+"flag:"+flag+"-----------------------------");
				String issueIndexURL1 = indexURL.replaceAll("list", "view_"+flag);
				String pageUrl=issueIndexURL1.replaceAll(".html", pageNum+".html");
				String pageResponseContent= HttpClientUtil.getResponseContent(pageUrl, "UTF-8");
				//从另一个Content获取到ImageURL和Article
				Matcher imageMattcher=RegexUtil.matcher(jobConfig.getPageImageRegex(), pageResponseContent);
				while(imageMattcher.find()){
					String pageImageURL=imageMattcher.group(1);
					Page page = new Page(pageName, pageNum, pageUrl, issue.getJob());
					page.setIssue(issue);
					page.setPageImageURL(pageImageURL);
					pageSet.add(page);
					// 指定版次号抓取
					DebugControlCenter.specifyPageNumberCrawl(page, pageSet);
				}
			}
			
			issue.getPageQueue().addAll(pageSet);
		}
		else{
			logIssueNotFound(issue);
			logParsePageSize(jobConfig.getPaperName(), issue.getIssueDate(), pageSet);
		}
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

	
	
