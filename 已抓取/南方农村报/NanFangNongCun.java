package com.apabi.crawler.job.impl;

import com.apabi.crawler.job.DefaultJob;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.ParsePageArticleUtil;
import com.apabi.crawler.util.ParsePageUtil;

public class NanFangNongCun extends DefaultJob {
	@Override
	public void parsePage() {
		ParsePageUtil.parsePage(issue, 3, 1, 2);
	}
	
	public void parsePageArticle(Page page, String pageResponseContent) {
		ParsePageArticleUtil.parsePageArticle(page, pageResponseContent, 1, 2, 3 ,4, 5);
	}
	
}
