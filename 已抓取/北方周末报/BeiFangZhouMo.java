package com.apabi.crawler.job.impl;

import com.apabi.crawler.job.ParsePageURLNumberNameJob;
import com.apabi.crawler.model.Page;
import com.apabi.crawler.util.ParsePageArticleUtil;

public class BeiFangZhouMo extends ParsePageURLNumberNameJob {
	@Override
	public void parsePageArticle(Page page, String pageResponseContent) {
		ParsePageArticleUtil.parsePageArticle(page, pageResponseContent, 1, 2);
	}
}
