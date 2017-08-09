package com.apabi.crawler.job.impl;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
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
import com.apabi.crawler.job.ParsePageURLNumberNameJob;
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
 *
 *中国航天报:
 *先抓包  抓到
 *http://pub.paper.ios-open.com/index.php?c=SeePaper&m=allkanqi&callback=jQuery211018922542205097548_1502248969968&paperid=7&type=1&_=1502248969969 
 *
 *做get模拟抓到内容   获取日期对应ID  把id替换进URL
 *
 */
public class ZhongGuoHangTian extends ParsePageURLNumberNameJob {
	private static Logger LOGGER = LoggerFactory.getLogger(ZhongGuoHangTian.class);

	@Override
	public void parsePage() {
		int pageURLIndex = 1, pageNumberIndex = 2, pageNameIndex = 3;
		Set<Page> pageSet = new HashSet<Page>();
		JobConfig jobConfig = issue.getJobConfig();
		String issueIndexURL = "";
		// 获取日期
		Date issueDate = issue.getIssueDate();
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(issueDate);

		// 格式化日期为00000000
		SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
		String date = dateFormat.format(calendar.getTime());
		// 获取URL
		issueIndexURL = getURL(date);
		String issueIndexResponseContent = HttpClientUtil.getResponseContent(issueIndexURL, issue.getJob());

		if (StringUtils.isNotBlank(issueIndexResponseContent)) {

			int groupCount = 3;

			String pageRegex = CrawlChain.getRegex(jobConfig.getPageRegex(),
					GlobalJobConfig.getInstance().getPageRegexList(), groupCount, issueIndexResponseContent);

			if (pageRegex != null) {
				Matcher matcher = RegexUtil.matcher(pageRegex, issueIndexResponseContent);
				pageSet = new HashSet<Page>();
				while (matcher.find()) {

					String pageName = matcher.group(pageNameIndex);

					String pageNumber = matcher.group(pageNumberIndex);
					pageNumber = FilterUtil.pageNumberFilter(pageNumber);

					String pageURL = matcher.group(pageURLIndex);
					pageURL = CrawlerUtil.getAbsoluteURL(issueIndexURL, pageURL);

//					String pagePDFURLIndex = jobConfig.getPagePDFRegex();
//					String pagePDFURL;
//					if (pagePDFURLIndex != null) {
//						pagePDFURL = matcher.group(pagePDFURLIndex);
//						pagePDFURL = CrawlerUtil.getAbsoluteURL(issueIndexURL, pagePDFURL);
//					}

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
		} else {
			logIssueNotFound(issue);
		}

	}

	public static String getURL(String date) {
		String id = "";
		String URL = "";
		String getURL = "http://pub.paper.ios-open.com/index.php?c=SeePaper&m=allkanqi&callback=jQuery211018922542205097548_1502248969968&paperid=7&type=1&_=1502248969969";
		CloseableHttpClient client = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet(getURL);
		try {
			CloseableHttpResponse response = client.execute(httpGet);
			HttpEntity entity = response.getEntity();
			InputStream in = entity.getContent();
			String result = IOUtils.toString(in);
			// System.out.println(result);
			String regexID = "\\{\"id\":\"(\\d{2})\"[\\s\\S]{0,70}\"pushTime\":\"" + date;
			Pattern pattern = Pattern.compile(regexID);
			Matcher matcher = pattern.matcher(result);

			while (matcher.find()) {
				id = matcher.group(1);
				// System.out.println(id);
			}

			String dateURL = "http://ep.cannews.com.cn/publish/zghkb7/html/00/sec_000001.html";
			String regexURL = "/\\d{2}";
			URL = dateURL.replaceAll(regexURL, "/" + id);
			// System.out.println(URL);

		} catch (ClientProtocolException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		} catch (IOException e) {
			// TODO 自动生成的 catch 块
			e.printStackTrace();
		}

		return URL;

	}
	
	
	
	public static void logParseIssuePageFailure(Issue issue) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("期次◆★");
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●").append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆版次pageRegex★链式匹配失败");
		LOGGER.warn(logBuffer.toString());
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
	
	public static void logParsePageSize(String paperName, Date issueDate, Set<Page> pageSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("解析出◆★").append(paperName).append("●").append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("★◆版面数: ").append(pageSet.size());
		LOGGER.info(logBuffer.toString());
	}
	
}
