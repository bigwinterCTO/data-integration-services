package com.aimspeed.gatherer.service.action;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.aimspeed.common.datatype.CollectionUtils;
import com.aimspeed.common.enums.IsDeleteEnum;
import com.aimspeed.gatherer.entity.bean.mongo.content.ContentDataMongoBean;
import com.aimspeed.gatherer.entity.bean.mysql.content.ExecutedUrlMySqlBean;
import com.aimspeed.gatherer.entity.bean.mysql.rule.CommonRuleMySqlBean;
import com.aimspeed.gatherer.entity.bean.mysql.rule.CrawlerMySqlBean;
import com.aimspeed.gatherer.entity.bean.mysql.rule.ExtractRuleMySqlBean;
import com.aimspeed.gatherer.entity.bean.mysql.rule.PagingIndexMySqlBean;
import com.aimspeed.gatherer.entity.vo.result.ResultVo;
import com.aimspeed.gatherer.repository.mongo.content.ContentDataMongoBeanMapper;
import com.aimspeed.gatherer.service.content.ExecutedUrlService;
import com.aimspeed.gatherer.service.httpclient.HttpClientResult;
import com.aimspeed.gatherer.service.rule.CommonRuleService;
import com.aimspeed.gatherer.service.rule.CrawlerService;
import com.aimspeed.gatherer.service.rule.ExtractRuleService;
import com.aimspeed.gatherer.service.rule.PagingIndexService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

/**
 * 数据采集运行类
 * @author AimSpeed
 */
@Service
@Scope(scopeName=ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GathererActionServiceImpl implements GathererActionService {
	

	private final Logger log = LoggerFactory.getLogger(GathererActionServiceImpl.class);
	
	@Autowired
	private CrawlerService crawlerBeanService;
	
	@Autowired
	private ExecutedUrlService executedUrlBeanService; 
	
	@Autowired
	private CommonRuleService commonRuleBeanService;
	
//	@Autowired
//	private ContentDataService contentDataBeanService;
	
	@Autowired
	private ExtractRuleService extractRuleBeanService;
	
	@Autowired
	private PagingIndexService pagingIndexBeanService;
	
	@Autowired
	private ContentDataMongoBeanMapper contentDataBeanMongoMapper;
	
	/*
	 * 按照Id开始采集者的数据采集
	 * @author AimSpeed
	 * @Title startCrawlingData
	 * @param loginAccount
	 * @param crawlerId
	 * @return
	 * @overridden @see com.aimspeed.gatherer.service.gatherer.CreeperBeanService#startCrawlingData(java.lang.String, java.lang.Integer)
	 * @date 2018年3月27日
	 */
	@Override
	public ResultVo startCrawlingData(String loginAccount, Integer crawlerId) {
		//获取到所有需要爬取的网站的数据
		CrawlerMySqlBean crawlerBean = crawlerBeanService.selectOfId(crawlerId);
		
		log.info("---------------------- 开始采集任务！");

		//查询公共提取规则
		CommonRuleMySqlBean commonRuleBeanSelect = new CommonRuleMySqlBean();
		commonRuleBeanSelect.setSequence(crawlerBean.getSequence());
		commonRuleBeanSelect.setIsDelete(IsDeleteEnum.N.getValue());
		CommonRuleMySqlBean commonRuleBean = commonRuleBeanService.selectOnlyOfSelective(commonRuleBeanSelect);
		
		//查询数据提取规则
		ExtractRuleMySqlBean extractRuleBeanSelect = new ExtractRuleMySqlBean();
		extractRuleBeanSelect.setSequence(crawlerBean.getSequence());
		extractRuleBeanSelect.setIsDelete(IsDeleteEnum.N.getValue());
		List<ExtractRuleMySqlBean> extractRuleBeans = extractRuleBeanService.selectSelective(extractRuleBeanSelect);
		
		//查询下一页规则
		PagingIndexMySqlBean pagingIndexBeanSelect = new PagingIndexMySqlBean();
		pagingIndexBeanSelect.setSequence(crawlerBean.getSequence());
		pagingIndexBeanSelect.setIsDelete(IsDeleteEnum.N.getValue());
		PagingIndexMySqlBean pagingIndexBean = pagingIndexBeanService.selectOnlyOfSelective(pagingIndexBeanSelect);
		
		/*List<String> urls = new ArrayList<>();
		
		//当前层则直接返回这个url
		if(0 == crawlerBean.getHierarchy()) {
			urls.add(crawlerBean.getUrl());
		}
		
		//一层则直接抓取这个页面的所有url返回，只有在第一层的时候才需要添加上请求参数作为过滤条件
		HttpClientResult httpClientResult = crawlerBeanService.crawlerRuleToRequest(crawlerBean.getSequence(), crawlerBean.getUrl());

		//停止器 - 每分钟抓取的访问次数，进行间隔
		try {
			Thread.sleep(60 / crawlerBean.getRequestNum() * 1000);
		} catch (InterruptedException e) {
			//e.printStackTrace();
			Thread.currentThread().interrupt();
		}
		
		urls = getPageAllUrl(crawlerBean.getMainUrl(),crawlerBean.getUrl(), httpClientResult.getResponseBody());
		
		//2层或以上则需要循环抓取，因为上面抓了一层所以要-1，如果是1层的话，那么则条件不匹配自动跳过
		for (int i = 0; i < crawlerBean.getHierarchy() - 1; i++) {
				
			//存放每一次临时抓取到的url
			List<String> tempUrls = new ArrayList<>();
			
			for (int j = 0; j < urls.size(); j++) {
				//抓取当前这个url
				httpClientResult = crawlerBeanService.crawlerRuleToRequest(crawlerBean.getSequence(), urls.get(j),true,false,true);
				
				//停止器 - 每分钟抓取的访问次数，进行间隔
				try {
					Thread.sleep(60 / crawlerBean.getRequestNum() * 1000);
				} catch (InterruptedException e) {
					//e.printStackTrace();
					Thread.currentThread().interrupt();
				}
				
				//获取到当前这个层级的这个链接这个页面下的所有url
				List<String> newUrls = getPageAllUrl(crawlerBean.getMainUrl(),urls.get(j), httpClientResult.getResponseBody());
				tempUrls.addAll(newUrls);
			}
			
			//如果继续有层级，那么则继续下去，否则则返回。
			urls = tempUrls;
		}
		*/
		
		//获取到要请求的所有URL
		List<String> urls = getRequestUrls(crawlerBean);
		
		//获取到已经执行过的URL
		Map<String, ExecutedUrlMySqlBean>  executeUrlMaps = getExecuteUrls(crawlerBean.getSequence());
		
		//抓取数据
		for (int i = 0; i < urls.size(); i++) {
			//判断是否抓取过，抓取过则跳过
			if(null != executeUrlMaps.get(urls.get(i))) {
				continue;
			}
			
			//抓取页面
			List<ContentDataMongoBean> contentDataBeans = capturePage(loginAccount, crawlerBean, urls.get(i), 
																	commonRuleBean, extractRuleBeans, pagingIndexBean);

			//停止器 - 每分钟抓取的访问次数，进行间隔
			try {
				Thread.sleep(60 / crawlerBean.getRequestNum() * 1000);
			} catch (InterruptedException e) {
//				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
			
			if(null != contentDataBeans && contentDataBeans.size() > 0) {
				
//				Integer insertCount = contentDataBeanService.batchInsert(contentDataBeans);
				contentDataBeanMongoMapper.insert(contentDataBeans);
			}
		}
		
		log.info("---------------------- 数据任务执行完毕！");
		return null;
	}
	
	/**
	 * 获取到要请求的所有URL
	 * @author AimSpeed
	 * @Title getRequestUrls 
	 * @param crawlerBean
	 * @return List<String> 
	 * @date 2018年9月29日
	 */
	private List<String> getRequestUrls(CrawlerMySqlBean crawlerBean) {
		List<String> urls = new ArrayList<>();
		
		//当前层则直接返回这个url
		if(0 == crawlerBean.getHierarchy()) {
			urls.add(crawlerBean.getUrl());
		}
		
		//一层则直接抓取这个页面的所有url返回，只有在第一层的时候才需要添加上请求参数作为过滤条件
		HttpClientResult httpClientResult = crawlerBeanService.crawlerRuleToRequest(crawlerBean.getSequence(), crawlerBean.getUrl());

		//停止器 - 每分钟抓取的访问次数，进行间隔
		try {
			Thread.sleep(60 / crawlerBean.getRequestNum() * 1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
		urls = getPageAllUrl(crawlerBean.getMainUrl(),crawlerBean.getUrl(), httpClientResult.getResponseBody());
		
		//2层或以上则需要循环抓取，因为上面抓了一层所以要-1，如果是1层的话，那么则条件不匹配自动跳过
		for (int i = 0; i < crawlerBean.getHierarchy() - 1; i++) {
				
			//存放每一次临时抓取到的url
			List<String> tempUrls = new ArrayList<>();
			
			for (int j = 0; j < urls.size(); j++) {
				//抓取当前这个url
				httpClientResult = crawlerBeanService.crawlerRuleToRequest(crawlerBean.getSequence(), urls.get(j),true,false,true);
				
				//停止器 - 每分钟抓取的访问次数，进行间隔
				try {
					Thread.sleep(60 / crawlerBean.getRequestNum() * 1000);
				} catch (InterruptedException e) {
					//e.printStackTrace();
					Thread.currentThread().interrupt();
				}
				
				//获取到当前这个层级的这个链接这个页面下的所有url
				List<String> newUrls = getPageAllUrl(crawlerBean.getMainUrl(),urls.get(j), httpClientResult.getResponseBody());
				tempUrls.addAll(newUrls);
			}
			
			//如果继续有层级，那么则继续下去，否则则返回。
			urls = tempUrls;
		}
		
		return urls;
	}
	
	/**
	 * 通过页面数据获取到页面内的所有链接
	 * @author AimSpeed
	 * @Title getPageAllUrl 
	 * @param mainUrl
	 * @param currentUrl
	 * @param responseBody
	 * @return List<String> 
	 * @date 2018年3月27日
	 */
	private List<String> getPageAllUrl(String mainUrl,String currentUrl, String responseBody) {
		//一个页面的所有链接
		List<String> urls = new ArrayList<>();
		Document document = Jsoup.parse(responseBody);
		if(null != document){
			Elements elements = document.getElementsByTag("a");
			
			if(null != elements && elements.size() > 0){
				//获取到当前页面的所有链接
				for (Element element : elements) {
					String hrefUrl = element.attr("href");
					//获取到完整的url
					hrefUrl = fullUrl(mainUrl,hrefUrl);
					
					//抓取的url，必须要包含网站url和配置的主url，但是能是等于当前的这个url，避免出现死循环
					if(hrefUrl.contains(mainUrl) && hrefUrl.contains(currentUrl) && !hrefUrl.equals(currentUrl)) {
						urls.add(hrefUrl);
					}
				}
				CollectionUtils.removeDuplicate(urls);//去重
				return urls;
			}
		}
		return urls;
	}

	/**
	 * 判断是否是完整的url地址，不是则填充完整的url，是则直接返回
	 * @author AimSpeed
	 * @Title fullUrl 
	 * @param mainUrl
	 * @param url
	 * @return String 
	 * @date 2018年3月27日
	 */
	private String fullUrl(String mainUrl,String url) {
		//将URL地址转换为小写的
		String lowerUrl = url.toLowerCase();
		
		//判断是否是有完整url特征（www、http、https）
		boolean flag = lowerUrl.startsWith("www") || lowerUrl.startsWith("http:") || lowerUrl.startsWith("https:");
		if(!flag) {
			url = url.replaceFirst("/", "");
			return mainUrl + url;
		}
		return url;		
	}
	
	/**
	 * 获取到这个配置下已经执行抓取的URL
	 * @author AimSpeed
	 * @Title isExecute 
	 * @param sequence
	 * @param url
	 * @return boolean 
	 * @date 2018年3月27日
	 */
	private Map<String, ExecutedUrlMySqlBean> getExecuteUrls(String sequence) {
		//查询这个网站执行过的抓取页面。
		ExecutedUrlMySqlBean executedUrlMySql = new ExecutedUrlMySqlBean();
		executedUrlMySql.setSequence(sequence);
		executedUrlMySql.setIsDelete(IsDeleteEnum.N.getValue());
		List<ExecutedUrlMySqlBean> executedUrlMySqlBeans = executedUrlBeanService.selectSelective(executedUrlMySql);
		
		//组装数据 URL = 配置对象
		Map<String, ExecutedUrlMySqlBean> result = new HashMap<>();
		if(null != executedUrlMySqlBeans && executedUrlMySqlBeans.size() > 0) {
			for (ExecutedUrlMySqlBean executedUrlMySqlBean : executedUrlMySqlBeans) {
				result.put(executedUrlMySqlBean.getExecutedUrl(), executedUrlMySqlBean);
			}
		}
		return result;
	}
	
	/**
	 * 是否执行过这个url抓取数据
	 * @author AimSpeed
	 * @Title isExecute 
	 * @param sequence
	 * @param url
	 * @return boolean 
	 * @date 2018年3月27日
	 */
	/*private boolean isExecute(String sequence,String url) {
		
		Map<String, Object> condition = new HashMap<>();
		condition.put("sequence", sequence);
		condition.put("executed_url", url);
		condition.put("is_delete", IsDeleteEnum.N.getValue());
		Integer count = executedUrlBeanService.selectDataCountSize(condition, null);
		
		if(count > 0) {
			return true;
		}
		return false;
	}*/
	
	/**
	 * 抓取页面
	 * @author AimSpeed
	 * @Title capturePage 
	 * @param loginAccount
	 * @param crawlerBean
	 * @param captureUrl
	 * @param commonRuleBean
	 * @param extractRuleBeans
	 * @param pagingIndexBean
	 * @return List<ContentDataBean> 
	 * @date 2018年3月27日
	 */
	public List<ContentDataMongoBean> capturePage(String loginAccount,CrawlerMySqlBean crawlerBean,String captureUrl,CommonRuleMySqlBean commonRuleBean,
									List<ExtractRuleMySqlBean> extractRuleBeans,PagingIndexMySqlBean pagingIndexBean  ) {
		//未执行过的url，才进行抓取
//		if(!isExecute(crawlerBean.getSequence(),captureUrl)) {
			
			//抓取当前这个url的页面数据
			HttpClientResult httpClientResult = crawlerBeanService.crawlerRuleToRequest(crawlerBean.getSequence(), captureUrl,true,false,true);
			if(null != httpClientResult && null != httpClientResult.getResponseBody()) {
				
				//将源码字符串进行转换为文档
				Document document = Jsoup.parse(httpClientResult.getResponseBody());
				if(null != document) {
					//解析抓取回来的Html，提取出这个数据
					List<ContentDataMongoBean> result = parseHtml(loginAccount, crawlerBean, captureUrl, document, 
													commonRuleBean, extractRuleBeans, pagingIndexBean);
					
					//存储抓取过url记录
					ExecutedUrlMySqlBean executedUrlBean = new ExecutedUrlMySqlBean();
					executedUrlBean.setExecutedUrl(captureUrl);
					executedUrlBean.setMethod(crawlerBean.getMethod());
					executedUrlBean.setSequence(crawlerBean.getSequence());
					executedUrlBean.setIsDelete(IsDeleteEnum.N.getValue());
					executedUrlBean.setCreator(loginAccount);
					executedUrlBean.setUpdator(loginAccount);
					executedUrlBeanService.insertSelective(executedUrlBean);
					
					/*
					 * 如果有下一页，那么则进行下一页操作
					 */
					if(null != pagingIndexBean) {
						//获取到下一页的url
						String nextUrl = getNextPage(crawlerBean.getMainUrl(), captureUrl, document, pagingIndexBean);
						
						if(null != nextUrl && !"".equals(nextUrl.trim())) {
							
							//获取到下一页的页面Html源码
							httpClientResult = crawlerBeanService.crawlerRuleToRequest(crawlerBean.getSequence(), nextUrl,true,false,true);

							//停止器 - 每分钟抓取的访问次数，进行间隔
							try {
								Thread.sleep(60 / crawlerBean.getRequestNum() * 1000);
							} catch (InterruptedException e) {
//								e.printStackTrace();
								Thread.currentThread().interrupt();
							}
							
							if(null != httpClientResult && null != httpClientResult.getResponseBody()) {
								
								//将源码字符串进行转换为文档
								document = Jsoup.parse(httpClientResult.getResponseBody());
								
								if(null != document) {
									//解析抓取回来的Html，提取出这个数据，提取下一页的数据，则将当前Url换成下一页的url
									List<ContentDataMongoBean> nextPageResult = parseHtml(loginAccount, crawlerBean, nextUrl, document, 
																				commonRuleBean, extractRuleBeans, pagingIndexBean);
									
									//存储抓取过url记录
									executedUrlBean.setExecutedUrl(nextUrl);
									executedUrlBean.setIsDelete(IsDeleteEnum.N.getValue());
									executedUrlBeanService.insertSelective(executedUrlBean);
									
									//将所有数据都放到同一个集合中
									if(null != nextPageResult && nextPageResult.size() > 0) {
										result.addAll(nextPageResult);
									}
								}
									
								
							}
							
						}
					}
					return result;
				}
			}
//		}
		return null;
	}
	
	/**
	 * 解析Html源代码
	 * @author AimSpeed
	 * @Title parseHtml 
	 * @param loginAccount
	 * @param crawlerBean
	 * @param currentUrl
	 * @param document
	 * @param commonRuleBean
	 * @param extractRuleBeans
	 * @param pagingIndexBean
	 * @return List<ContentDataBean> 
	 * @date 2018年3月27日
	 */
	private List<ContentDataMongoBean> parseHtml(String loginAccount,CrawlerMySqlBean crawlerBean,String currentUrl,
											Document document,CommonRuleMySqlBean commonRuleBean,
											List<ExtractRuleMySqlBean> extractRuleBeans,PagingIndexMySqlBean pagingIndexBean) {
		
		//去除无效的数据
		document.select("script").remove();
		document.select("style").remove();
		document.select("link").remove();
		
		//提取当前页面的，所有公共代码块
		Elements elements = extractCommomHtml(document,commonRuleBean);
		
		if(null != elements && elements.size() > 0) {
			//提取公共代码块的数据
			List<ContentDataMongoBean> result = extractHtmlData(loginAccount,crawlerBean,currentUrl,elements,extractRuleBeans);
			return result;
		}
		return null;
	}
		
	/**
	 * 根据公共标签规则提取所有公共HTML代码
	 * @author AimSpeed
	 * @Title extractCommomHtml 
	 * @param document
	 * @param commonRuleBean
	 * @return Elements 
	 * @date 2018年3月27日
	 */
	private Elements extractCommomHtml(Document document,CommonRuleMySqlBean commonRuleBean) {
		//获取到公共标签规则
		Map<String,String> commonHmlMap = JSON.parseObject(commonRuleBean.getRule(), LinkedHashMap.class);
		Elements elements = null;
		for (Entry<String, String> entry : commonHmlMap.entrySet()) {
			//因为目前只存储一个属性标签名
			String key = entry.getKey();//标识
			String value = entry.getValue();//标签名
			elements = document.getElementsByTag(value);
		}
		return elements;
	}
	
	/**
	 * 解析每一个公共代码块，根据规则进行提取数据
	 * @author AimSpeed
	 * @Title extractHtmlData 
	 * @param loginAccount
	 * @param crawlerBean
	 * @param sourceUrl
	 * @param elements
	 * @param extractRuleBeans
	 * @return List<ContentDataBean> 
	 * @date 2018年3月27日
	 */
	private List<ContentDataMongoBean> extractHtmlData(String loginAccount,CrawlerMySqlBean crawlerBean,String sourceUrl,Elements elements,List<ExtractRuleMySqlBean> extractRuleBeans) {
		
		log.info("---------------------- 开始提取数据！");
		
//		List<ContentDataMySqlBean> result = new ArrayList();
		List<ContentDataMongoBean> result = new ArrayList();
		
		ContentDataMongoBean contentDataMongoBean = new ContentDataMongoBean();
		contentDataMongoBean.setSequence(crawlerBean.getSequence());
		contentDataMongoBean.setCrawlerName(crawlerBean.getName());
		contentDataMongoBean.setDataClassify(crawlerBean.getDataClassify());
		contentDataMongoBean.setContentUrl(sourceUrl);
		
		Map<String,Object> content = new HashMap<>();//内容数据
		//获取出每一个的公共代码
		for (Element element : elements) {
			
			String dataChainUuid = UUID.randomUUID().toString();
			
			//根据规则解析每一个公共代码，进行提取数据
			for (ExtractRuleMySqlBean extractRuleBean : extractRuleBeans) {
				
				//根据每一个解析规则，解析数据
				String text = parseElementByRule(element,extractRuleBean);
				
				if(null != text) {
					content.put(extractRuleBean.getContentClassify(), text);
					//存储每一个内容数据
					/*ContentDataMySqlBean contentDataBean = new ContentDataMySqlBean();
					contentDataBean.setSequence(crawlerBean.getSequence());
					contentDataBean.setCrawlerName(crawlerBean.getName());
					contentDataBean.setDataClassify(crawlerBean.getDataClassify());
					contentDataBean.setContentClassify(extractRuleBean.getContentClassify());
					contentDataBean.setContentUrl(sourceUrl);
					contentDataBean.setContent(text);
					contentDataBean.setDataUuid(UUID.randomUUID().toString());
					contentDataBean.setDataChainUuid(dataChainUuid);
					contentDataBean.setIsDelete(IsDeleteEnum.N.getValue());
					contentDataBean.setCreator(loginAccount);
					contentDataBean.setUpdator(loginAccount);*/
					
					
				}
			}
		}
		contentDataMongoBean.setContent(content);
		contentDataMongoBean.setDataUuid(UUID.randomUUID().toString());
		contentDataMongoBean.setIsDelete(IsDeleteEnum.N.getValue());
		contentDataMongoBean.setCreator(loginAccount);
		contentDataMongoBean.setCreateTime(new Date());
		contentDataMongoBean.setUpdator(loginAccount);
		contentDataMongoBean.setUpdateTime(new Date());
		result.add(contentDataMongoBean);
		return result;
	}
	
	/**
	 * 根据每一个解析规则，解析数据
	 * @author AimSpeed
	 * @Title parseElementByRule 
	 * @param element
	 * @param extractRuleBean
	 * @return String 
	 * @date 2018年3月27日
	 */
	private String parseElementByRule(Element element,ExtractRuleMySqlBean extractRuleBean) {
		
		//获取到规则
		String rule = extractRuleBean.getRule();
		
		//转义解析JSON
		Map<String,Object> ruleMap = JSON.parseObject(rule, LinkedHashMap.class);
		String tagName = (String) ruleMap.get("tag");//标签名
		Integer tagIndex = (Integer) ruleMap.get("index");//标签位置
		
		//根据标签和位置获取到
		Elements elements = element.getElementsByTag(tagName);
		
		if(null != elements && elements.size() > 0 && elements.size() > tagIndex) {
			
			//根据标签位置进行获取
			Element elementText = elements.get(tagIndex);
			String text = elementText.text();
			
			//是否有子标签，有子标签则继续
			JSONObject subTagJson = (JSONObject) ruleMap.get("subTag");
			if(null != subTagJson) {
				extractRuleBean.setRule(subTagJson.toJSONString());
				return parseElementByRule(elementText,extractRuleBean);
			}
			
			//没有子标签，则直接进行返回
			if(null != text) {
				return text;
			}
		}
		return null;
	}
	
	/**
	 * 获取到下一页，获取到则爬去下一页
	 * @author AimSpeed
	 * @Title getNextPage 
	 * @param mainUrl
	 * @param currentUrl
	 * @param document
	 * @param pagingIndexBean
	 * @return String 
	 * @date 2018年3月27日
	 */
	public String getNextPage(String mainUrl,String currentUrl,Document document,PagingIndexMySqlBean pagingIndexBean) {
		//获取到规则JSON
		String ruleJson = pagingIndexBean.getRule();
		
		//获取到提取规则
		Map<String,Object> pagingIndexRule = JSON.parseObject(ruleJson, LinkedHashMap.class);
		String tagName = (String) pagingIndexRule.get("tag");
		String idVal = (String) pagingIndexRule.get("id");
		String classVal = (String) pagingIndexRule.get("class");
		
		//选择器字符拼接
		String selectStr = null;
		selectStr = tagName;
		if(null != classVal && classVal.length() > 0) {
			selectStr += "[class=" + classVal + "]";
		}
		
		if(null != idVal && idVal.length() > 0) {
			selectStr += "#" + idVal;
		}
		
		//选择器进行查找
		Elements elementsHref = document.select(selectStr);
		
		//获取不到，有可能到了最后一页，进行了隐藏
		if(null != elementsHref && elementsHref.size() > 0) {
			Element element = elementsHref.first();
			String url = element.attr("href");
			
			if(null != url && url.length() > 0) {
				//是否是完整URL，判断是否要补充完整
				url = fullUrl(mainUrl, url);
				
				//判断这个URL是否等于当前这个url，如果等于那么，则为最一页
				if(url.equalsIgnoreCase(currentUrl)) {
					return null;
				}else {
					return url;
				}
			}
		}
		return null;
	}
	
	
}
