package cc.utime.marketingagent.parser;

import cc.utime.marketingagent.domain.CampaignIntent;

public interface CampaignIntentParser {

  CampaignIntent parse(String requirement);
}
