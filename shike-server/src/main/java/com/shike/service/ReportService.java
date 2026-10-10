package com.shike.service;

import com.shike.vo.OrderReportVO;
import com.shike.vo.SalesTop10ReportVO;
import com.shike.vo.TurnoverReportVO;
import com.shike.vo.UserReportVO;

import javax.servlet.http.HttpServletResponse;
import java.time.LocalDate;

public interface ReportService {
    TurnoverReportVO getTurnoverReport(LocalDate begin, LocalDate end);

    UserReportVO getUserReport(LocalDate begin, LocalDate end);

    OrderReportVO getOrderReport(LocalDate begin, LocalDate end);

    SalesTop10ReportVO getSalesTop10Report(LocalDate begin, LocalDate end);

    void exportBusinessData(HttpServletResponse response);
}
