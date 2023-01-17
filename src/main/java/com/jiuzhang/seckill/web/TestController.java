package com.jiuzhang.seckill.web;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Controller
public class TestController {

    @ResponseBody
    @RequestMapping("hello")
    public String hello() {
        String result;

        try (Entry entry = SphU.entry("HelloResource")) {
            // protected logic
            result = "Hello Sentinel!";
            return result;
        } catch (BlockException ex) {
            // block resource access, throttle or downgrade
            log.error(ex.toString());
            result = "System busy, please try later";
            return result;
        }
    }

    /**
     * Define throttling rules
     * 1. create a set to store rules of throttling
     * 2. create throttling rules
     * 3. put rules into the set
     * 4. load the rules
     * @PostContruct means to execute after constructor finishes
     */
    @PostConstruct
    public void seckillFlow() {
        // seckills list rules
        // 1. create a set to store rules of throttling
        List<FlowRule> rules = new ArrayList<>();
        // 2. create throttling rules
        FlowRule rule = new FlowRule();
        // set up resource, so that Sentinel will be effective to the corresponding resource
        rule.setResource("seckills");
        // define throttling rule types (type is QPS)
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        // define QPS = query per second to be 2
        rule.setCount(1);

        // HelloResource rules
        FlowRule rule2 = new FlowRule();
        rule2.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule2.setResource("HelloResource");
        rule2.setCount(2);

        // 3. put rules into the set
        rules.add(rule);
        rules.add(rule2);
        // load the rules
        FlowRuleManager.loadRules(rules);
    }
}
