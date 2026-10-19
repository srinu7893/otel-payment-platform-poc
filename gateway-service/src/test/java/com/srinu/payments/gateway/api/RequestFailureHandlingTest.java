package com.srinu.payments.gateway.api;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
class RequestFailureHandlingTest {
    @RestController static class Probe {
        @PostMapping("/probe") String post(@RequestBody Map<String,Integer> body){return "ok";}
        @GetMapping("/probe/{id}") String id(@PathVariable("id") UUID id){return id.toString();}
        @GetMapping("/required") String query(@RequestParam("value") String value){return value;}
        @GetMapping("/failure") String failure(){throw new RuntimeException("password=SECRET-MUST-NOT-LEAK");}
    }
    MockMvc mvc=MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new ApiExceptionHandler()).build();
    @Test void malformedJsonAndWrongFieldTypesAreClientErrors() throws Exception {
        for(String body:new String[]{"{","{\"amount\":\"invalid\"}",""})
            mvc.perform(post("/probe").contentType("application/json").content(body)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test void invalidIdentifiersAndMissingParametersAreClientErrors() throws Exception {
        mvc.perform(get("/probe/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/required")).andExpect(status().isBadRequest());
    }
    @Test void unsupportedMethodAndContentTypeAreNotInternalFailures() throws Exception {
        mvc.perform(put("/probe").contentType("application/json").content("{}")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/probe").contentType("text/plain").content("{}")).andExpect(status().isUnsupportedMediaType());
    }
    @Test void unexpectedExceptionIsSafeAndRemainsAnError() throws Exception {
        mvc.perform(get("/failure")).andExpect(status().isInternalServerError()).andExpect(content().string(not(containsString("SECRET"))));
    }
}
