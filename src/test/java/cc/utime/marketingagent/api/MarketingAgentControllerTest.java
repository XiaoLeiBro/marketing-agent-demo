package cc.utime.marketingagent.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cc.utime.marketingagent.MarketingAgentApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = MarketingAgentApplication.class)
@AutoConfigureMockMvc
class MarketingAgentControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void shouldCreateDraftByApi() throws Exception {
    this.mockMvc.perform(
            post("/api/marketing-agent/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"福建新用户满30减5预算10万\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
        .andExpect(jsonPath("$.draft.region").value("福建"))
        .andExpect(jsonPath("$.draft.couponRule").value("满30减5"));
  }

  @Test
  void shouldRejectBlankRequirement() throws Exception {
    this.mockMvc.perform(
            post("/api/marketing-agent/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requirement\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("requirement")));
  }

  @Test
  void shouldSearchSamples() throws Exception {
    this.mockMvc.perform(get("/api/marketing-agent/knowledge/samples").param("query", "福建新用户满30减5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].region").value("福建"));
  }
}
