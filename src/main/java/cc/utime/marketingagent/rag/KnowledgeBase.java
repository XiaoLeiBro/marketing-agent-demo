package cc.utime.marketingagent.rag;

import cc.utime.marketingagent.domain.CampaignSample;
import java.util.List;

public interface KnowledgeBase {

  List<CampaignSample> searchSimilarCampaigns(String query, int topK);
}
