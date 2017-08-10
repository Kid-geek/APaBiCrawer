package com.apabi.crawler.job.impl;

import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.http.Consts;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.apabi.crawler.filter.FilterUtil;
import com.apabi.crawler.job.DefaultJob;
import com.apabi.crawler.model.Article;
import com.apabi.crawler.model.ArticleImage;
import com.apabi.crawler.model.GlobalJobConfig;
import com.apabi.crawler.model.Issue;
import com.apabi.crawler.model.JobConfig;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.CoordinateUtil;
import com.apabi.crawler.util.CrawlChain;
import com.apabi.crawler.util.CrawlerUtil;
import com.apabi.crawler.util.DebugControlCenter;
import com.apabi.crawler.util.HttpClientUtil;
import com.apabi.crawler.util.ParseArticleUtil;
import com.apabi.crawler.util.ParsePageArticleUtil;
import com.apabi.crawler.util.RegexUtil;

/**
 * @author zh
 * 
 *   乐清日报
 *   Post模拟登录
 *
 */
public class LeQingRiBao extends DefaultJob {
	private static Logger LOGGER = LoggerFactory.getLogger(LeQingRiBao.class);

	@Override
	public void parsePage() {
		int pageURLIndex = 1, pageNumberIndex = 2, pageNameIndex = 3, pagePDFURLIndex = 4;
		Set<Page> pageSet = new HashSet<Page>();
		JobConfig jobConfig = issue.getJobConfig();
		// 生成期次首页链接地址
		String issueIndexURL = issue.getIssueIndexURLTemplate().replaceAll(jobConfig.getDateRegex(),
				DateFormatUtils.format(issue.getIssueDate(), jobConfig.getDatePattern()));

		CloseableHttpClient closeableHttpClient = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet(issueIndexURL);
		httpGet.setHeader("Accept", "text/html,application/xhtml+xml," + "application/xml;q=0.9,image/webp,*/*;q=0.8");
		httpGet.setHeader("Accept-Encoding", "gzip, deflate, sdch, br");
		httpGet.setHeader("Accept-Language", "zh-CN,zh;q=0.8");
		// 重点在Cookie 通过登录成功后的页面控制台复制cookie即可
		httpGet.setHeader("Cookie",
				"ASP.NET_SessionId=y4fu1hystff1od55gxqrptej; username=apabi; userpassword=Founder123; userguid=25375c11-da9f-4f1e-8562-1c9fc166c202; _trs_uv=1ajl_81_j65rx93g; _trs_ua_s_1=1k47_81_j65rx93g; key=HJICJBIJIJ");
		httpGet.setHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36"
				+ " (KHTML, like Gecko) Chrome/55.0.2883.87 Safari/537.36");

		CloseableHttpResponse closeableHttpResponse = null;
		try {
			closeableHttpResponse = closeableHttpClient.execute(httpGet);
		} catch (ClientProtocolException e1) {
			// TODO 自动生成的 catch 块
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO 自动生成的 catch 块
			e1.printStackTrace();
		}
		String entity = null;
		if (closeableHttpResponse.getStatusLine().getStatusCode() == 200) {
			// 得到响应实体
			try {

				entity = EntityUtils.toString(closeableHttpResponse.getEntity(), "utf-8");
			} catch (ParseException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
			} catch (IOException e) {
				// TODO 自动生成的 catch 块
				e.printStackTrace();
			}
			// System.out.println(entity);
		}

		String issueIndexResponseContent = entity;

		if (StringUtils.isNotBlank(issueIndexResponseContent)) {

			int groupCount = 3;
			if (pagePDFURLIndex != 0) {
				groupCount = 4;
			}
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
			} else {
				logParseIssuePageFailure(issue);
			}
		} else {
			logIssueNotFound(issue);
		}
	}

	@Override
	public void parsePageImage(Page page, String pageResponseContent) {
		pageResponseContent = setCookie(page.getPageURL());

		if (page.getPageImageURL() == null) {
			String pageImageRegex = CrawlChain.getRegex(jobConfig.getPageImageRegex(),
					GlobalJobConfig.getInstance().getPageImageRegexList(), pageResponseContent);

			if (pageImageRegex != null) {
				String pageImageURL = RegexUtil.getGroup1MatchContent(pageImageRegex, pageResponseContent);
				pageImageURL = CrawlerUtil.getAbsoluteURL(page.getPageURL(), pageImageURL);
				LoggerFactory.getLogger(getClass())
						.debug(jobConfig.getPaperName() + CrawlerUtil.dateNormalFormat(issue.getIssueDate()) + "◆★"
								+ page.getPageNumber() + "-" + page.getPageName() + "★◆版面图URL: " + pageImageURL);
				page.setPageImageURL(pageImageURL);
			} else {
				StringBuffer logBuffer = new StringBuffer();
				logBuffer.append(jobConfig.getPaperName()).append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()))
						.append("◆★");
				logBuffer.append(page.getPageNumber()).append("-").append(page.getPageName());
				logBuffer.append("★◆版面图pageImageRegex★链式匹配失败");
				LoggerFactory.getLogger(getClass()).warn(logBuffer.toString());
			}
		}
	}

	@Override
	public void parsePageArticle(Page page, String pageResponseContent) {

		int articleURLIndex = 2;
		int coordinateIndex = 1;
		pageResponseContent = setCookie(page.getPageURL());
		if (page.getPagePDFURL() == null) {
			String pagePDFRegex = CrawlChain.getRegex(page.getIssue().getJobConfig().getPagePDFRegex(),
					GlobalJobConfig.getInstance().getPagePDFRegexList(), pageResponseContent,
					page.getIssue().getJobConfig().getPaperName() + "★版面PDFpagePDFRegex★链式匹配失败");

			if (pagePDFRegex != null) {
				String pagePDFURL = RegexUtil.getGroup1MatchContent(pagePDFRegex, pageResponseContent);
				pagePDFURL = CrawlerUtil.getAbsoluteURL(page.getPageURL(), pagePDFURL);
				LOGGER.debug(page.getIssue().getJobConfig().getPaperName()
						+ CrawlerUtil.dateNormalFormat(page.getIssue().getIssueDate()) + "◆★" + page.getPageNumber()
						+ "-" + page.getPageName() + "★◆版面PDFURL: " + pagePDFURL);
				page.setPagePDFURL(pagePDFURL);
			}
		}

		String articleRegex = CrawlChain.getRegex(page.getIssue().getJobConfig().getArticleRegex(),
				GlobalJobConfig.getInstance().getArticleRegexList(), 2, pageResponseContent,
				page.getIssue().getJobConfig().getPaperName() + "★版面articleRegex★链式匹配失败");
		if (articleRegex != null) {
			Set<Article> articleSet = new LinkedHashSet<Article>();
			Matcher matcher = RegexUtil.matcher(articleRegex, pageResponseContent);
			while (matcher.find()) {
				String articleURL = matcher.group(articleURLIndex);
				// 去除类似content_75574.htm?div=-1, 后面的?div=-1
				// if (articleURL.contains("?")) {
				// articleURL = articleURL.substring(0,
				// articleURL.lastIndexOf("?"));
				// }
				articleURL = CrawlerUtil.getAbsoluteURL(page.getPageURL(), articleURL);

				String coordinate = matcher.group(coordinateIndex);
				Set<double[]> articleCoordinateSet = CoordinateUtil.getCoordinateSet(coordinate, page);
				Article article = new Article(articleURL, articleCoordinateSet, page.getJob());

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
	}

	@Override
	public void parseArticle(Page page) {
		JobConfig jobConfig = page.getIssue().getJobConfig();

		if (page.getArticleSet() != null) {
			Iterator<Article> articleIterator = page.getArticleSet().iterator();
			while (articleIterator.hasNext()) {
				Article article = articleIterator.next();
				// 设置头

				final String articleResponseContent = setCookie(article.getArticleURL());
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
					ParseArticleUtil.logParseArticle(jobConfig.getPaperName(), page.getIssue().getIssueDate(),
							page.getPageNumber(), page.getPageName(), articleTitle);

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
						if (StringUtils.isNotBlank(articleContent)) {
							articleContent = articleContent.replaceAll("<P mce_keep=\"true\">&nbsp;</P>", "");
						}
						articleContent = FilterUtil.trimFilter(articleContent);
					}

					String articleImageRegex = CrawlChain.getRegex(jobConfig.getArticleImageRegex(),
							GlobalJobConfig.getInstance().getArticleImageRegexList(), articleResponseContent,
							jobConfig.getPaperName() + "★稿件articleImageRegex★链式匹配失败");
					Set<ArticleImage> articleImageSet = null;
					if (articleImageRegex != null) {
						articleImageSet = ParseArticleUtil.getArticleImageSet(articleImageRegex, articleResponseContent,
								article.getArticleURL(), article.getJob());
						ParseArticleUtil.logParseArticleImageSize(jobConfig.getPaperName(),
								page.getIssue().getIssueDate(), page.getPageNumber(), page.getPageName(),
								articleImageSet);
					}

					article.extend(articleAuthor, articleSource, articleIntrotitle, articleTitle, articleSubTitle,
							articleContent, articleImageSet);
				} else {
					articleIterator.remove();
				}
			}
		}

	}

	public static String setCookie(String pageURL) {
		CloseableHttpClient closeableHttpClient = HttpClients.createDefault();
		// 创建Post请求实例
		HttpPost httpPost = new HttpPost("http://www.yqrb.cn/check/UserLogin.aspx");

		// 创建参数列表
		List<NameValuePair> valuePairs = new LinkedList<NameValuePair>();
		valuePairs.add(new BasicNameValuePair("__VIEWSTATE",
				"/wEPDwUKLTYyMjc0MTMwNWQYAQUeX19Db250cm9sc1JlcXVpcmVQb3N0QmFja0tleV9fFgIFEExvZ2luMSRDaGVja0JveDEFD0xvZ2luMSRpYnRMb2dpblgHoKZ58APGc4h5Aq8tcf+WuKlv"));
		valuePairs.add(new BasicNameValuePair("Login1$txtUserName", "apabi"));
		valuePairs.add(new BasicNameValuePair("Login1$txtUserPassWord", "Founder123"));
		valuePairs.add(new BasicNameValuePair("Login1$CheckBox1", "on"));
		valuePairs.add(new BasicNameValuePair("Login1$ibtLogin.x", "0"));
		valuePairs.add(new BasicNameValuePair("Login1$ibtLogin.y", "0"));
		valuePairs.add(new BasicNameValuePair("__EVENTVALIDATION",
				"/wEWBQL2/JnoBQLGrKnLCQLZ3e7ECAKUkrPDCgKGkYz4DFU4WWL55rJshJkm7OKlQUqgsNqQ"));

		UrlEncodedFormEntity entity = new UrlEncodedFormEntity(valuePairs, Consts.UTF_8);
		httpPost.setEntity(entity);
		try {
			closeableHttpClient.execute(httpPost);// 登录
		} catch (ClientProtocolException e2) {
			e2.printStackTrace();
		} catch (IOException e2) {
			e2.printStackTrace();
		}
		//get获取网页内容
		HttpGet httpGet = new HttpGet(pageURL);
		
		CloseableHttpResponse closeableHttpResponse = null;
		 try {
		 closeableHttpResponse = closeableHttpClient.execute(httpGet);
		 } catch (ClientProtocolException e1) {
		 e1.printStackTrace();
		 } catch (IOException e1) {
		 e1.printStackTrace();
		 }
		
		 String data = null ;
		 if (closeableHttpResponse.getStatusLine().getStatusCode() == 200) {
		 // 得到响应实体
		 try {
		 data = EntityUtils.toString(closeableHttpResponse.getEntity(),
		 "utf-8");
		 } catch (ParseException e) {
		 e.printStackTrace();
		 } catch (IOException e) {
		 e.printStackTrace();
		 }
		// System.out.println(entity);
		 }
		

		return data;

	}

	public static void logParseIssuePageFailure(Issue issue) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("期次◆★");
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●")
				.append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆版次pageRegex★链式匹配失败");
		LOGGER.warn(logBuffer.toString());
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
		logBuffer.append(issue.getJobConfig().getPaperName()).append("●")
				.append(CrawlerUtil.dateNormalFormat(issue.getIssueDate()));
		logBuffer.append("★◆不存在, 期次首页: ");
		if (StringUtils.isNotBlank(issue.getIssueIndexURLTemplate())) {
			logBuffer.append(issue.getIssueIndexURLTemplate().replaceAll(issue.getJobConfig().getDateRegex(),
					DateFormatUtils.format(issue.getIssueDate(), issue.getJobConfig().getDatePattern())));
		}
		LOGGER.info(logBuffer.toString());
	}

	public static void logParseArticle(String paperName, Date issueDate, String pageNumber, String pageName,
			String articleURL) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append(", 解析出◆★").append(pageNumber).append("-").append(pageName).append("★◆, 稿件URL: ")
				.append(articleURL);
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

}
