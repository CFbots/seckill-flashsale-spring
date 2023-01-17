package com.jiuzhang.seckill.web;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson.JSON;
import com.jiuzhang.seckill.db.dao.OrderDao;
import com.jiuzhang.seckill.db.dao.SeckillActivityDao;
import com.jiuzhang.seckill.db.dao.SeckillCommodityDao;
import com.jiuzhang.seckill.db.po.Order;
import com.jiuzhang.seckill.db.po.SeckillActivity;
import com.jiuzhang.seckill.db.po.SeckillCommodity;
import com.jiuzhang.seckill.services.SeckillActivityService;
import com.jiuzhang.seckill.util.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
public class SeckillActivityController {

    @Autowired
    private SeckillActivityDao seckillActivityDao;
    @Autowired
    private SeckillCommodityDao seckillCommodityDao;
    @Autowired
    SeckillActivityService seckillActivityService;
    @Autowired
    OrderDao orderDao;
    @Autowired
    private RedisService redisService;

    public static final String ORDER_SUCCESS_PROMPT = "Congratulations! Creating your order (Order ID: )";
    public static final String ORDER_FAILURE_PROMPT = "";
    public static final String STOCK_INSUFFICIENT_PROMPT = "";

    @RequestMapping("/addSeckillActivity")
    public String addSeckillActivity() {
        return "add_activity";
    }

    @RequestMapping("/addSeckillActivityAction")
    public String addSeckillActivity(
            @RequestParam("name") String name,
            @RequestParam("commodityId") long commodityId,
            @RequestParam("seckillPrice") BigDecimal seckillPrice,
            @RequestParam("oldPrice") BigDecimal oldPrice,
            @RequestParam("seckillNumber") long seckillNumber,
            @RequestParam("startTime") String startTime,
            @RequestParam("endTime") String endTime,
            Map<String, Object> resultMap
    ) throws ParseException {
        startTime = startTime.substring(0, 10) + startTime.substring(11);
        endTime = endTime.substring(0, 10) + endTime.substring(11);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-ddhh:mm");
        SeckillActivity seckillActivity = new SeckillActivity();
        seckillActivity.setName(name);
        seckillActivity.setCommodityId(commodityId);
        seckillActivity.setSeckillPrice(seckillPrice);
        seckillActivity.setOldPrice(oldPrice);
        seckillActivity.setTotalStock(seckillNumber);
        seckillActivity.setAvailableStock(new Integer("" + seckillNumber));
        seckillActivity.setLockStock(0L);
        seckillActivity.setActivityStatus(1);
        seckillActivity.setStartTime(format.parse(startTime));
        seckillActivity.setEndTime(format.parse(endTime));
        seckillActivityDao.inertSeckillActivity(seckillActivity);

        resultMap.put("seckillActivity", seckillActivity);
        return "add_success";
    }

    @RequestMapping("/seckills")
    public String activityList(Map<String, Object> resultMap) {
        try (Entry entry = SphU.entry("seckills")) {
            List<SeckillActivity> seckillActivities =
                    seckillActivityDao.querySeckillActivitysByStatus(1);
            resultMap.put("seckillActivities", seckillActivities);
            return "seckill_activity";
        } catch (BlockException ex) {
            log.error("View activityList() is throttled: " + ex.toString());
            return "wait";
        }
    }

    @RequestMapping("/item/{seckillActivityId}")
    public String itemPage(Map<String, Object> resultMap, @PathVariable long seckillActivityId) {
        SeckillActivity seckillActivity; // = seckillActivityDao.querySeckillActivityById(seckillActivityId);
        SeckillCommodity seckillCommodity; // = seckillCommodityDao.querySeckillCommodityById(seckillActivity.getCommodityId());

        String seckillActivityInfo = redisService.getValue("seckillActivity:" + seckillActivityId);
        if (StringUtils.isNotEmpty(seckillActivityInfo)) {
            log.info("redis cache data: " + seckillActivityInfo);
            seckillActivity = JSON.parseObject(seckillActivityInfo, SeckillActivity.class);
        } else {
            seckillActivity = seckillActivityDao.querySeckillActivityById(seckillActivityId);
        }

        String seckillCommodityInfo = redisService.getValue("seckillCommodity:" + seckillActivity.getCommodityId());
        if (StringUtils.isNotEmpty(seckillCommodityInfo)) {
            log.info("redis cache data: " + seckillCommodityInfo);
            seckillCommodity = JSON.parseObject(seckillCommodityInfo, SeckillCommodity.class); // presuming typo in doc // original: seckillActivityInfo, not seckillCommodityInfo
        } else {
            seckillCommodity = seckillCommodityDao.querySeckillCommodityById(seckillActivity.getCommodityId());
        }

        resultMap.put("seckillActivity", seckillActivity);
        resultMap.put("seckillCommodity", seckillCommodity);
        resultMap.put("seckillPrice", seckillActivity.getSeckillPrice());
        resultMap.put("oldPrice", seckillActivity.getOldPrice());
        resultMap.put("commodityId", seckillActivity.getCommodityId());
        resultMap.put("commodityName", seckillCommodity.getCommodityName());
        resultMap.put("commodityDesc", seckillCommodity.getCommodityDesc());

        return "seckill_item";
    }

    /**
     * Process seckill purchase request
     * @param userId
     * @param seckillActivityId
     * @return
     */
    @RequestMapping("/seckill/buy/{userId}/{seckillActivityId}")
    public ModelAndView seckillCommodity(@PathVariable long userId, @PathVariable long seckillActivityId) {
        boolean stockValidateResult = false;
        ModelAndView modelAndView = new ModelAndView();

        try {
            // determine if user is already in purchased list
            if (redisService.isInLimitMember(seckillActivityId, userId)) {
                // notifies user is already in limit list, return the result
                modelAndView.addObject("resultInfo", "Sorry, you are already in the limit list");
                modelAndView.setViewName("seckill_result");
                return modelAndView;
            }

            // determine if seckill purchase is available
            stockValidateResult = seckillActivityService.seckillStockValidator(seckillActivityId);

            if (stockValidateResult) {
                Order order = seckillActivityService.createOrder(seckillActivityId, userId);
                modelAndView.addObject(
                        "resultInfo",
                        String.format("Congratulations! Creating your order (Order ID: %s)", order.getOrderNo())
                );
                modelAndView.addObject("orderNo", order.getOrderNo());
                // add user to limit order as a member
                redisService.addLimitMember(seckillActivityId, userId);
            } else {
                modelAndView.addObject(
                        "resultInfo",
                        "Sorry, the item you ordered is out of stock"
                );
            }
        } catch (Exception e) {
            log.error("System error: " + e.toString());
            modelAndView.addObject("resultInfo", "System error: order placement failed");
        }

        modelAndView.setViewName("seckill_result");
        return modelAndView;
    }

    @RequestMapping("/seckill/orderQuery/{orderNo}")
    public ModelAndView orderQuery(@PathVariable String orderNo) {
        log.info("orderQuery(), orderNo: " + orderNo);
        Order order = orderDao.queryOrder(orderNo);
        ModelAndView modelAndView = new ModelAndView();

        if (order != null) {
            modelAndView.setViewName("order");
            modelAndView.addObject("order", order);
            SeckillActivity seckillActivity = seckillActivityDao.querySeckillActivityById(order.getSeckillActivityId());
            modelAndView.addObject("seckillActivity", seckillActivity);
        } else {
            modelAndView.setViewName("order_wait");
        }

        return modelAndView;
    }

    @RequestMapping("/seckill/payOrder/{orderNo}")
    public String payOrder(@PathVariable String orderNo) throws Exception {
        seckillActivityService.payOrderProcess(orderNo);
        return "redirect:/seckill/orderQuery/" + orderNo;
    }

    @ResponseBody
    @RequestMapping("/seckill/getSystemTime")
    public String getSystemTime() {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = df.format(new Date());
        return date;
    }
}
