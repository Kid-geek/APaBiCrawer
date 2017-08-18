package com.apabi.crawler.job.impl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.apabi.crawler.filter.FilterUtil;
import com.apabi.crawler.job.ParsePageURLNumberNameJob;
import com.apabi.crawler.model.GlobalJobConfig;
import com.apabi.crawler.model.Issue;
import com.apabi.crawler.model.JobConfig;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.CrawlChain;
import com.apabi.crawler.util.CrawlerUtil;
import com.apabi.crawler.util.DebugControlCenter;
import com.apabi.crawler.util.RegexUtil;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;

public class FuZhouRiBao extends ParsePageURLNumberNameJob {
	private static final Logger LOGGER = LoggerFactory.getLogger(FuZhouRiBao.class);

	@Override
	public void parsePage() {
		// int pageURLIndex=1; int pageNumberIndex=2; pageNameIndex=3;
		// pagePDFURLIndex=0;
		Set<Page> pageSet = null;
		JobConfig jobConfig = issue.getJobConfig();

		// 生成期次首页链接地址
		String issueIndexURL = issue.getIssueIndexURLTemplate().replaceAll(jobConfig.getDateRegex(),
				DateFormatUtils.format(issue.getIssueDate(), jobConfig.getDatePattern()));

		// 创建浏览器做js渲染
		WebClient wc = new WebClient(BrowserVersion.FIREFOX_24);
		wc.setJavaScriptTimeout(5000);
		wc.getOptions().setUseInsecureSSL(true);// 接受任何主机连接 无论是否有有效证书
		wc.getOptions().setJavaScriptEnabled(true);// 设置支持javascript脚本
		wc.getOptions().setCssEnabled(false);// 禁用css支持
		wc.getOptions().setThrowExceptionOnScriptError(false);// js运行错误时不抛出异常
		wc.getOptions().setTimeout(100000);// 设置连接超时时间
		wc.getOptions().setDoNotTrackEnabled(false);
		HtmlPage page1;
		try {
			page1 = wc.getPage(issueIndexURL);
			
			String res = page1.asXml();
			// 获取版次select列表
			Pattern r = Pattern.compile(
					"<select id=\"page_Num_Name\" width=\"200px\" style=\"margin-top:10px;margin-bottom:10px\">([\\s\\S]*?)</select>");
			Matcher m = r.matcher(res);
			String select = null;
			while (m.find()) {
				select = m.group(0); // Select列表
				// System.out.println("src"+m.group(0));
			}
			// 获取版次链接
			Pattern r1 = Pattern.compile("value=\"([\\s\\S]{70,76})\"");
			Matcher m1 = r1.matcher(select);
			String rootUrl = "http://mag.fznews.com.cn";
			List<String> pageUrlList = new ArrayList<String>();// 版次链接列表
			List<String> pageNumberList = new ArrayList<String>();
			List<String> pageNameList = new ArrayList<String>();
			List<String> articalUrlList = new ArrayList<String>();// 文章链接列表
			List<String> articalCoorList=new ArrayList<String>();
			// 获取版次Number Name
			Pattern rNumber = Pattern.compile("([\\d]{3})版：([\\s\\S]{0,10})");
			Matcher mNumber = rNumber.matcher(select);
			while (mNumber.find()) {
				pageNumberList.add(mNumber.group(1));
				pageNameList.add(mNumber.group(2));
			}

			while (m1.find()) {
				// select=m.group(0);
				// System.out.println("src: "+m1.group(1));
				String pageUrl = rootUrl + m1.group(1);
				pageUrlList.add(pageUrl);

				// 获取JS渲染后的网页
				page1 = wc.getPage(rootUrl + m1.group(1));
//				System.out.println("pageURL:  " + rootUrl + m1.group(1));
				String resPage = page1.asXml();
				// System.out.println(resPage);
				Pattern r2 = Pattern.compile("<area[\\s\\S]*?coords=\"(.*?)\" href=\"(.*?)\"/>");
				Matcher m2 = r2.matcher(resPage);

//				// 获取文章链接
				while (m2.find()) {
					String articalCoor=m2.group(1);
					String articalUrl = rootUrl + m2.group(2);
					System.out.println("articalURL:  " + rootUrl + m2.group(2));
					articalCoorList.add(articalCoor);
					articalUrlList.add(articalUrl);
				}

			}

			pageSet = new HashSet<Page>();

			for (int i = 0; i < pageNameList.size(); i++) {
				String pageName = pageNameList.get(i);
				String pageNumber = pageNumberList.get(i);
				pageNumber = FilterUtil.pageNumberFilter(pageNumber);
				String pageURL = pageUrlList.get(i);
				Page page = new Page(pageName, pageNumber, pageURL, issue.getJob());
				page.setIssue(issue);
				
				// 指定版次号抓取
				DebugControlCenter.specifyPageNumberCrawl(page, pageSet);
			}
			issue.getPageQueue().addAll(pageSet);
		logParsePageSize(jobConfig.getPaperName(), issue.getIssueDate(), pageSet);
		} catch (FailingHttpStatusCodeException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
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
}
