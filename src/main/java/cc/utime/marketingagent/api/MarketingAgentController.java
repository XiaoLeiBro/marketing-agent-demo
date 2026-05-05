package cc.utime.marketingagent.api;

import cc.utime.marketingagent.application.MarketingAgentService;
import cc.utime.marketingagent.domain.AgentTrace;
import cc.utime.marketingagent.domain.CampaignSample;
import cc.utime.marketingagent.domain.CreateDraftRequest;
import cc.utime.marketingagent.domain.CreateDraftResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/marketing-agent")
public class MarketingAgentController {

  private final MarketingAgentService marketingAgentService;

  public MarketingAgentController(MarketingAgentService marketingAgentService) {
    this.marketingAgentService = marketingAgentService;
  }

  @PostMapping("/drafts")
  public CreateDraftResponse createDraft(@RequestBody @Valid CreateDraftRequest request) {
    return this.marketingAgentService.createDraft(request.requirement());
  }

  @GetMapping("/traces/{traceId}")
  public AgentTrace getTrace(@PathVariable String traceId) {
    return this.marketingAgentService.getTrace(traceId);
  }

  @GetMapping("/knowledge/samples")
  public List<CampaignSample> searchSamples(
      @RequestParam String query,
      @RequestParam(defaultValue = "3") int topK) {
    return this.marketingAgentService.searchSamples(query, topK);
  }
}
