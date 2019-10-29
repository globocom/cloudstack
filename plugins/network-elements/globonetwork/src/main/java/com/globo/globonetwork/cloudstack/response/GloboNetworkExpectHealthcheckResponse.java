package com.globo.globonetwork.cloudstack.response;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.Command;
import java.util.List;

public class GloboNetworkExpectHealthcheckResponse extends Answer{

    public GloboNetworkExpectHealthcheckResponse(Command command, boolean success, String details, List<ExpectedHealthcheck> expectedHealthchecks) {
        super(command, success, details);
        this.expectedHealthchecks = expectedHealthchecks;
    }

    public GloboNetworkExpectHealthcheckResponse(List<ExpectedHealthcheck> expectedHealthchecks) {
        this.expectedHealthchecks = expectedHealthchecks;
    }

    private List<ExpectedHealthcheck> expectedHealthchecks;

    public List<ExpectedHealthcheck> getExpectedHealthchecks() {
        return expectedHealthchecks;
    }

    public static class ExpectedHealthcheck {
        private Long id;
        private String expected;

        public ExpectedHealthcheck() {
        }

        public ExpectedHealthcheck(Long id, String expected) {
            this.id = id;
            this.expected = expected;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getExpected() {
            return expected;
        }

        public void setExpected(String expected) {
            this.expected = expected;
        }
    }
}
