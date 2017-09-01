package com.apabi.crawler.job.impl;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.time.DateFormatUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
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
import com.apabi.crawler.job.Job;
import com.apabi.crawler.job.ParsePageURLNumberNameJob;
import com.apabi.crawler.model.Article;
import com.apabi.crawler.model.ArticleImage;
import com.apabi.crawler.model.GlobalJobConfig;
import com.apabi.crawler.model.JobConfig;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.CrawlChain;
import com.apabi.crawler.util.CrawlerUtil;
import com.apabi.crawler.util.DebugControlCenter;
import com.apabi.crawler.util.RegexUtil;

public class ChangShuRiBao extends ParsePageURLNumberNameJob {
	private static Logger LOGGER = LoggerFactory.getLogger(ChangShuRiBao.class);

	@Override
	public void parsePage() {
		Set<Page> pageSet = new HashSet<Page>();
		JobConfig jobConfig = issue.getJobConfig();
		String issueIndexURL = issue.getIssueIndexURLTemplate().replaceAll(jobConfig.getDateRegex(),
				DateFormatUtils.format(issue.getIssueDate(), jobConfig.getDatePattern()));

		CloseableHttpClient closeableHttpClient = HttpClients.createDefault();
		HttpGet httpGet = new HttpGet(issueIndexURL);
		CloseableHttpResponse closeableHttpResponse = null;
		String pageEntity = null;
		String dgId = null, date = null;
		String listEntity = null;

		String pageName = null, pageNumber = null, pageURL = null;

		try {
			closeableHttpResponse = closeableHttpClient.execute(httpGet);
			if (closeableHttpResponse.getStatusLine().getStatusCode() == 200) {
				// 得到响应实体
				try {
					pageEntity = EntityUtils.toString(closeableHttpResponse.getEntity(), "utf-8");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			Pattern pagePattern = Pattern
					.compile("var dgId = '([\\s\\S]{0,11})';[\\s\\S]*?var date = '([\\s\\S]{0,10})';");
			Matcher pageMatcher = pagePattern.matcher(pageEntity);
			if (pageMatcher.find()) {
				dgId = pageMatcher.group(1);
				date = pageMatcher.group(2);
			}

			String pageListURL = "http://dzb.csxww.com/forumsList.action?&dgId=" + dgId + "&date=" + date;
			CloseableHttpClient listcloseableHttpClient = HttpClients.createDefault();
			HttpGet ListhttpGet = new HttpGet(pageListURL);
			CloseableHttpResponse ListcloseableHttpResponse = listcloseableHttpClient.execute(ListhttpGet);
			listEntity = EntityUtils.toString(ListcloseableHttpResponse.getEntity(), "utf-8");
			Pattern listPattern = Pattern
					.compile("第([\\d]{0,2})版:[\\s\\S]{0,20}</i>&nbsp;&nbsp; ([\\s\\S]{0,15})</span></a>");
			Matcher listMatcher = listPattern.matcher(listEntity);
			while (listMatcher.find()) {
				pageNumber = listMatcher.group(1);
				pageName = listMatcher.group(2);
				pageURL = "http://dzb.csxww.com/forumsArticle.action?&dgId="+dgId+"&pageId="+pageNumber+"&itemType=Title&date="+date+"&products=32058101-1&rightList="+dgId;
				Page page = new Page(pageName, pageNumber, pageURL, issue.getJob());
				page.setIssue(issue);
				// 指定版次号抓取
				DebugControlCenter.specifyPageNumberCrawl(page, pageSet);
			}

		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		issue.getPageQueue().addAll(pageSet);
		logParsePageSize(jobConfig.getPaperName(), issue.getIssueDate(), pageSet);

	}

	
	@Override
	public void parsePageArticle(Page page, String pageResponseContent) {
		Pattern idPattern = Pattern.compile("dgId=([\\s\\S]{0,12})&pageId=([\\s\\S]{0,2})&");
		Matcher idMatcher = idPattern.matcher(page.getPageURL());
		String data = DateFormatUtils.format(issue.getIssueDate(), "yyyyMMdd");
		String dgId = null;
		String pageId = null;
		while (idMatcher.find()) {
			dgId = idMatcher.group(1);
			pageId = idMatcher.group(2);
		}
		
		String articleId = null;
		String jsId = null;

		Set<Article> articleSet = new LinkedHashSet<Article>();
		
		Pattern articlePattern = Pattern.compile("getArticle\\('([\\s\\S]{0,2})','([\\s\\S]{0,6})'\\);");
		Matcher articleMatcher = articlePattern.matcher(pageResponseContent);
		while (articleMatcher.find()) {
			articleId = articleMatcher.group(1);
			jsId = articleMatcher.group(2);

			String articleURL = "http://dzb.csxww.com/articleContent.action?&dgId="+dgId+"&pageId="+pageId+"&date="+data+"&articleId="+articleId+"&jsId="+jsId+"&products=32058101-1";
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
				String articleIndexURL = article.getArticleURL();
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
		String pageImageURL = "http://dzb.csxww.com/page4pic.action?width=360&height=550&pageId="+pageId+"&dgId="+dgId;
		pageImageURL = CrawlerUtil.getAbsoluteURL(page.getPageURL(), pageImageURL);
		LoggerFactory.getLogger(getClass())
				.debug(jobConfig.getPaperName() + CrawlerUtil.dateNormalFormat(issue.getIssueDate()) + "◆★"
						+ page.getPageNumber() + "-" + page.getPageName() + "★◆版面图URL: " + pageImageURL);
		page.setPageImageURL(pageImageURL);
	}
	
	
	
	
	// 获取网页源码
		public String getURLContent(String URL) {
			CloseableHttpClient httpclient = HttpClients.createDefault();
			// 创建Post请求实例
			HttpPost httpPost = new HttpPost("http://dzb.csxww.com/json/login.action");
			// 创建参数列表
			List<NameValuePair> valuePairs = new LinkedList<NameValuePair>();
			valuePairs.add(new BasicNameValuePair("username", "17744408473"));
			valuePairs.add(new BasicNameValuePair("password", "apabi123"));
			// 向对方服务器发送Post请求
			// 将参数进行封装，提交到服务器端
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(valuePairs, Consts.UTF_8);
			httpPost.setEntity(entity);
			String result = null;
			try {
				httpclient.execute(httpPost);// 登录
				HttpGet httpget = new HttpGet(URL);
				CloseableHttpResponse response = httpclient.execute(httpget);
				result = EntityUtils.toString(response.getEntity(), "utf-8");
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return result;
		}
		
		
		
		//下载版面图
		public static void  downloadArticleImage(String ImgURL, String fileDirectory) {
			CloseableHttpClient httpclient = HttpClients.createDefault();
			// 创建Post请求实例
			HttpPost httpPost = new HttpPost("http://dzb.csxww.com/json/login.action");
			// 创建参数列表
			List<NameValuePair> valuePairs = new LinkedList<NameValuePair>();
			valuePairs.add(new BasicNameValuePair("username", "17744408473"));
			valuePairs.add(new BasicNameValuePair("password", "apabi123"));
			// 向对方服务器发送Post请求
			// 将参数进行封装，提交到服务器端
			UrlEncodedFormEntity entity = new UrlEncodedFormEntity(valuePairs, Consts.UTF_8);
			httpPost.setEntity(entity);
			String result=null;
			try {
				httpclient.execute(httpPost);// 登录
				HttpGet httpget = new HttpGet(ImgURL);
				CloseableHttpResponse response = httpclient.execute(httpget);
				HttpEntity entitty = response.getEntity();  
	            InputStream in = entitty.getContent();
	            FileOutputStream fileOutputStream = null;
	            byte[] data = new byte[1024];
	            int len = 0;
	            fileOutputStream = new FileOutputStream("E:/aa"+".png");
	            while ((len = in.read(data)) != -1) {
	            fileOutputStream.write(data, 0, len);
	            }
	            System.out.println("下载完成");
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	
	
	
	
	
	public static void logParsePageSize(String paperName, Date issueDate, Set<Page> pageSet) {
		StringBuffer logBuffer = new StringBuffer();
		logBuffer.append("解析出◆★").append(paperName).append("●").append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("★◆版面数: ").append(pageSet.size());
		LOGGER.info(logBuffer.toString());
	}
	public static void logParseArticle(String paperName, Date issueDate, String pageNumber, String pageName,
			String articleTitle) {
		StringBuffer logBuffer = new StringBuffer();

		logBuffer.append(paperName).append(CrawlerUtil.dateNormalFormat(issueDate));
		logBuffer.append("解析出◆★").append(pageNumber).append("-").append(pageName);
		logBuffer.append("★◆稿件URL: ").append(articleTitle);
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
