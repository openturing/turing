package com.viglet.turing.ext.templating.turing;

import com.viglet.turing.ext.templating.turing.util.TemplatingUtil;

public class TuringSearchResult {
	  private String keyWord;
	  private String summary;
	  private String link;
	  private String thumbnail;
	  private String name;
	  private String externalItemId;
	  private int totalNumberOfResults;
	  	  
	  public String getSummary()
	  {
	    return this.summary;
	  }
	  
	  public void setSummary(String summary)
	  {
	    this.summary = summary;
	  }
	  
	  public String getLink()
	  {
	    return this.link;
	  }
	  
	  public void setLink(String link)
	  {
	    this.link = link;
	  }
	  
	  public String getThumbnail()
	  {
	    return this.thumbnail;
	  }
	  
	  public void setThumbnail(String thumbnail)
	  {
	    this.thumbnail = thumbnail;
	  }
	  
	  public String getName()
	  {
	    return this.name;
	  }
	  
	  public void setName(String name)
	  {
	    this.name = name;
	  }
	  
	  public String getKeyWord()
	  {
	    return this.keyWord;
	  }
	  
	  public void setKeyWord(String keyWord)
	  {
	    this.keyWord = keyWord;
	  }
	  
	  public int getTotalNumberOfResults()
	  {
	    return this.totalNumberOfResults;
	  }
	  
	  public void setTotalNumberOfResults(int totalNumberOfResults)
	  {
	    this.totalNumberOfResults = totalNumberOfResults;
	  }
	  
	  public String getExternalItemId()
	  {
	    return this.externalItemId;
	  }
	  
	  public void setExternalItemId(String externalItemId)
	  {
	    this.externalItemId = externalItemId;
	  }
	  
	  public String toString()
	  {
	    return TemplatingUtil.getToString(this);
	  }
	}
