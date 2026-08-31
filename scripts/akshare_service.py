# -*- coding: utf-8 -*-
import argparse
import concurrent.futures
import datetime as dt
import json
import math
import re
import socket
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse

import akshare as ak
import pandas as pd
import requests


REQUEST_TIMEOUT = 7
ORIGINAL_REQUEST = requests.sessions.Session.request
SECTOR_CACHE = {}
SECTOR_CACHE_LOCK = threading.Lock()
SECTOR_MEMBER_CACHE = {}
STOCK_SECTOR_CACHE = {}
LEADER_FUNDAMENTALS_CACHE = {}


def request_with_timeout(self, method, url, **kwargs):
    kwargs.setdefault("timeout", REQUEST_TIMEOUT)
    return ORIGINAL_REQUEST(self, method, url, **kwargs)


requests.sessions.Session.request = request_with_timeout


def clean(value):
    if value is None:
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    if hasattr(value, "item"):
        value = value.item()
    if isinstance(value, (dt.date, dt.datetime, pd.Timestamp)):
        return value.strftime("%Y-%m-%d")
    if isinstance(value, float) and (math.isnan(value) or math.isinf(value)):
        return None
    return value


def number(value):
    value = clean(value)
    if value is None or value == "":
        return None
    try:
        return float(str(value).replace(",", "").replace("%", ""))
    except (TypeError, ValueError):
        return None


def text(value):
    value = clean(value)
    return None if value is None else str(value).strip()


def row_value(row, *names):
    for name in names:
        if name in row.index:
            value = clean(row[name])
            if value is not None and value != "":
                return value
    return None


def source(name, available, message=None):
    return {
        "name": name,
        "available": available,
        "message": message,
        "updatedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }


def load_profile(code):
    result = {"profile": {}, "concepts": [], "sources": [], "warnings": []}
    try:
        info = ak.stock_individual_info_em(symbol=code)
        pairs = {text(row.iloc[0]): clean(row.iloc[1]) for _, row in info.iterrows()}
        result["profile"].update({
            "companyName": text(pairs.get("股票简称")),
            "industry": text(pairs.get("行业")),
            "listDate": text(pairs.get("上市时间")),
            "totalMarketValue": number(pairs.get("总市值")),
            "floatMarketValue": number(pairs.get("流通市值")),
        })
        result["sources"].append(source("东方财富公司资料", True))
    except Exception as exc:
        result["sources"].append(source("东方财富公司资料", False, str(exc)))
        result["warnings"].append("东方财富公司资料暂不可用")

    try:
        business = ak.stock_zyjs_ths(symbol=code)
        if not business.empty:
            row = business.iloc[0]
            result["profile"].update({
                "mainBusiness": text(row_value(row, "主营业务")),
                "productTypes": text(row_value(row, "产品类型")),
                "productNames": text(row_value(row, "产品名称")),
                "businessScope": text(row_value(row, "经营范围")),
            })
            tags = []
            for field in ("产品类型", "产品名称"):
                raw = text(row_value(row, field))
                if raw:
                    tags.extend(re.split(r"[、,，;；/]+", raw))
            result["concepts"] = list(dict.fromkeys(tag.strip() for tag in tags if tag.strip()))[:10]
        result["sources"].append(source("同花顺主营资料", not business.empty))
    except Exception as exc:
        result["sources"].append(source("同花顺主营资料", False, str(exc)))
        result["warnings"].append("同花顺主营资料暂不可用")

    if not result["profile"].get("industry") or not result["profile"].get("mainBusiness"):
        try:
            profile = ak.stock_profile_cninfo(symbol=code)
            if not profile.empty:
                row = profile.iloc[0]
                fallbacks = {
                    "companyName": text(row_value(row, "公司名称")),
                    "industry": text(row_value(row, "所属行业")),
                    "listDate": text(row_value(row, "上市日期")),
                    "mainBusiness": text(row_value(row, "主营业务")),
                    "businessScope": text(row_value(row, "经营范围")),
                }
                for key, value in fallbacks.items():
                    if not result["profile"].get(key) and value:
                        result["profile"][key] = value
                result["profile"]["introduction"] = text(row_value(row, "机构简介"))
            result["sources"].append(source("巨潮资讯公司资料", not profile.empty))
        except Exception as exc:
            result["sources"].append(source("巨潮资讯公司资料", False, str(exc)))
            result["warnings"].append("巨潮资讯公司资料暂不可用")
    return result


def metric_from_group(group, aliases):
    for alias in aliases:
        exact = group[group["metric_name"].astype(str) == alias]
        if not exact.empty:
            row = exact.iloc[0]
            return number(row_value(row, "value")), number(row_value(row, "yoy"))
    for _, row in group.iterrows():
        metric_name = text(row_value(row, "metric_name")) or ""
        if any(alias in metric_name for alias in aliases):
            return number(row_value(row, "value")), number(row_value(row, "yoy"))
    return None, None


def normalize_ths_performance(frame):
    periods = []
    if frame.empty or "report_date" not in frame.columns:
        return periods
    for report_date, group in frame.groupby("report_date", sort=False):
        revenue, revenue_yoy_ratio = metric_from_group(
            group, ["operating_income_total", "营业总收入", "营业收入"])
        revenue_yoy, _ = metric_from_group(
            group, ["calculate_operating_income_total_yoy_growth_ratio"])
        if revenue_yoy is None and revenue_yoy_ratio is not None:
            revenue_yoy = revenue_yoy_ratio * 100

        net_profit, net_profit_yoy_ratio = metric_from_group(
            group, ["parent_holder_net_profit", "归母净利润", "归属净利润"])
        net_profit_yoy, _ = metric_from_group(
            group, ["calculate_parent_holder_net_profit_yoy_growth_ratio"])
        if net_profit_yoy is None and net_profit_yoy_ratio is not None:
            net_profit_yoy = net_profit_yoy_ratio * 100

        adjusted, adjusted_yoy_ratio = metric_from_group(
            group, ["index_deduct_holder_net_profit", "扣非净利润", "扣除非经常性损益后的净利润"])
        adjusted_yoy, _ = metric_from_group(group, ["deduct_net_profit_yoy_growth_ratio"])
        if adjusted_yoy is None and adjusted_yoy_ratio is not None:
            adjusted_yoy = adjusted_yoy_ratio * 100

        eps, _ = metric_from_group(group, ["basic_eps", "基本每股收益"])
        roe, _ = metric_from_group(group, ["index_weighted_avg_roe", "净资产收益率"])
        gross_margin, _ = metric_from_group(group, ["sale_gross_margin", "毛利率"])
        debt_ratio, _ = metric_from_group(group, ["assets_debt_ratio", "资产负债率"])
        operating_cash, _ = metric_from_group(
            group, ["index_per_operating_cash_flow_net", "经营活动产生的现金流量净额", "经营现金流"])
        first = group.iloc[0]
        periods.append({
            "reportDate": text(report_date),
            "reportName": text(row_value(first, "report_name", "report_period")),
            "revenue": revenue,
            "revenueYoY": revenue_yoy,
            "netProfit": net_profit,
            "netProfitYoY": net_profit_yoy,
            "adjustedNetProfit": adjusted,
            "adjustedNetProfitYoY": adjusted_yoy,
            "eps": eps,
            "roe": roe,
            "grossMargin": gross_margin,
            "debtRatio": debt_ratio,
            "operatingCashFlow": operating_cash,
            "source": "同花顺",
        })
    return periods[:12]


def normalize_em_performance(frame):
    periods = []
    for _, row in frame.head(12).iterrows():
        periods.append({
            "reportDate": text(row_value(row, "REPORT_DATE")),
            "reportName": text(row_value(row, "REPORT_DATE_NAME", "REPORT_TYPE")),
            "revenue": number(row_value(row, "TOTALOPERATEREVE")),
            "revenueYoY": number(row_value(row, "TOTALOPERATEREVETZ")),
            "netProfit": number(row_value(row, "PARENTNETPROFIT")),
            "netProfitYoY": number(row_value(row, "PARENTNETPROFITTZ")),
            "adjustedNetProfit": number(row_value(row, "KCFJCXSYJLR")),
            "adjustedNetProfitYoY": number(row_value(row, "KCFJCXSYJLRTZ")),
            "eps": number(row_value(row, "EPSJB")),
            "roe": number(row_value(row, "ROEJQ")),
            "grossMargin": number(row_value(row, "XSMLL")),
            "debtRatio": number(row_value(row, "ZCFZL")),
            "operatingCashFlow": number(row_value(row, "MGJYXJJE")),
            "source": "东方财富",
        })
    return periods


def load_performance(code, full_code):
    warnings = []
    sources = []
    try:
        frame = ak.stock_financial_abstract_new_ths(symbol=code, indicator="按报告期")
        periods = normalize_ths_performance(frame)
        if periods:
            sources.append(source("同花顺财务指标", True))
            return {"performance": periods, "sources": sources, "warnings": warnings}
    except Exception as exc:
        sources.append(source("同花顺财务指标", False, str(exc)))
        warnings.append("同花顺财务指标暂不可用")

    try:
        symbol = f"{code}.{full_code[:2].upper()}"
        frame = ak.stock_financial_analysis_indicator_em(symbol=symbol, indicator="按报告期")
        periods = normalize_em_performance(frame)
        sources.append(source("东方财富财务指标", bool(periods)))
        return {"performance": periods, "sources": sources, "warnings": warnings}
    except Exception as exc:
        sources.append(source("东方财富财务指标", False, str(exc)))
        warnings.append("财务指标暂不可用")
        return {"performance": [], "sources": sources, "warnings": warnings}


def load_valuation(code):
    try:
        frame = ak.stock_value_em(symbol=code)
        if frame.empty:
            return {"valuation": None, "sources": [source("东方财富估值", False, "暂无数据")], "warnings": ["暂无估值数据"]}
        row = frame.iloc[-1]
        valuation = {
            "date": text(row_value(row, "数据日期")),
            "closePrice": number(row_value(row, "当日收盘价")),
            "totalMarketValue": number(row_value(row, "总市值")),
            "peTtm": number(row_value(row, "PE(TTM)")),
            "peStatic": number(row_value(row, "PE(静)")),
            "pb": number(row_value(row, "市净率")),
            "peg": number(row_value(row, "PEG值")),
            "ps": number(row_value(row, "市销率")),
            "pcf": number(row_value(row, "市现率")),
            "industryPeTtm": None,
            "industryPb": None,
            "peRank": None,
            "peers": [],
        }
        return {"valuation": valuation, "sources": [source("东方财富估值", True)], "warnings": []}
    except Exception as exc:
        return {"valuation": None, "sources": [source("东方财富估值", False, str(exc))], "warnings": ["估值数据暂不可用"]}


def load_reports(code):
    try:
        frame = ak.stock_research_report_em(symbol=code)
        reports = []
        for _, row in frame.head(20).iterrows():
            eps_forecasts = {}
            pe_forecasts = {}
            for column in frame.columns:
                match = re.match(r"(\d{4})-盈利预测-(收益|市盈率)", str(column))
                if not match:
                    continue
                value = number(row[column])
                if value is None:
                    continue
                target = eps_forecasts if match.group(2) == "收益" else pe_forecasts
                target[match.group(1)] = value
            reports.append({
                "title": text(row_value(row, "报告名称")),
                "rating": text(row_value(row, "东财评级")),
                "institution": text(row_value(row, "机构")),
                "date": text(row_value(row, "日期")),
                "industry": text(row_value(row, "行业")),
                "pdfUrl": text(row_value(row, "报告PDF链接")),
                "epsForecasts": eps_forecasts,
                "peForecasts": pe_forecasts,
            })
        return {"researchReports": reports, "sources": [source("东方财富个股研报", bool(reports))], "warnings": []}
    except Exception as exc:
        return {"researchReports": [], "sources": [source("东方财富个股研报", False, str(exc))], "warnings": ["个股研报暂不可用"]}


def load_industry_position(full_code, code):
    valuation = None
    peers = []
    valuation_rank = None
    growth_rank = None
    sources = []
    warnings = []
    symbol = full_code.upper()
    try:
        frame = ak.stock_zh_valuation_comparison_em(symbol=symbol)
        target = frame[frame["代码"].astype(str).str.zfill(6) == code]
        if not target.empty:
            row = target.iloc[0]
            valuation_rank = text(row_value(row, "排名"))
        average = frame[frame["简称"].astype(str) == "行业平均"]
        median = frame[frame["简称"].astype(str) == "行业中值"]
        valuation = {
            "industryPeTtm": number(row_value(average.iloc[0], "市盈率-TTM")) if not average.empty else None,
            "industryPb": number(row_value(median.iloc[0], "市净率-MRQ")) if not median.empty else None,
            "peRank": valuation_rank,
        }
        for _, row in frame.iterrows():
            peer_code = text(row_value(row, "代码"))
            if not peer_code or not peer_code.isdigit():
                continue
            peers.append({
                "code": peer_code.zfill(6),
                "name": text(row_value(row, "简称")),
                "peTtm": number(row_value(row, "市盈率-TTM")),
                "pb": number(row_value(row, "市净率-MRQ")),
                "peg": number(row_value(row, "PEG")),
                "rank": text(row_value(row, "排名")),
            })
        sources.append(source("东方财富同行估值", True))
    except Exception as exc:
        sources.append(source("东方财富同行估值", False, str(exc)))
        warnings.append("同行估值比较暂不可用")

    try:
        frame = ak.stock_zh_growth_comparison_em(symbol=symbol)
        target = frame[frame["代码"].astype(str).str.zfill(6) == code]
        if not target.empty:
            row = target.iloc[0]
            rank_columns = [column for column in frame.columns if "净利润" in str(column) and "排名" in str(column)]
            if not rank_columns:
                rank_columns = [column for column in frame.columns if "排名" in str(column)]
            if rank_columns:
                growth_rank = text(row[rank_columns[0]])
        sources.append(source("东方财富同行成长", True))
    except Exception as exc:
        sources.append(source("东方财富同行成长", False, str(exc)))
        warnings.append("同行成长比较暂不可用")

    return {
        "industryPosition": {
            "industry": None,
            "valuationRank": valuation_rank,
            "growthRank": growth_rank,
            "roeRank": None,
            "scaleRank": None,
            "summary": "排名来自东方财富同行比较，分母与行业样本随数据源更新。",
        } if valuation_rank or growth_rank else None,
        "industryValuation": valuation,
        "peers": peers[:10],
        "sources": sources,
        "warnings": warnings,
    }


def build_snapshot(full_code):
    code = full_code[2:]
    jobs = {
        "profile": lambda: load_profile(code),
        "performance": lambda: load_performance(code, full_code),
        "valuation": lambda: load_valuation(code),
        "reports": lambda: load_reports(code),
        "industry": lambda: load_industry_position(full_code, code),
    }
    results = {}
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=len(jobs))
    futures = {name: executor.submit(job) for name, job in jobs.items()}
    done, pending = concurrent.futures.wait(futures.values(), timeout=REQUEST_TIMEOUT * 3)
    for name, future in futures.items():
        if future in done:
            try:
                results[name] = future.result()
            except Exception as exc:
                results[name] = {"warnings": [f"{name} 数据处理失败: {exc}"], "sources": []}
        else:
            future.cancel()
            results[name] = {"warnings": [f"{name} 数据请求超时"], "sources": []}
    executor.shutdown(wait=False, cancel_futures=True)

    profile_result = results.get("profile", {})
    valuation_result = results.get("valuation", {})
    industry_result = results.get("industry", {})
    valuation = valuation_result.get("valuation")
    if valuation and industry_result.get("industryValuation"):
        valuation.update(industry_result["industryValuation"])
        valuation["peers"] = industry_result.get("peers", [])

    profile = profile_result.get("profile") or None
    industry_position = industry_result.get("industryPosition")
    if industry_position and profile:
        industry_position["industry"] = profile.get("industry")

    sources = []
    warnings = []
    for result in results.values():
        sources.extend(result.get("sources", []))
        warnings.extend(result.get("warnings", []))
    performance = results.get("performance", {}).get("performance", [])
    reports = results.get("reports", {}).get("researchReports", [])
    available = bool(profile or performance or valuation or reports or industry_position)
    return {
        "fullCode": full_code,
        "available": available,
        "profile": profile,
        "performance": performance,
        "valuation": valuation,
        "researchReports": reports,
        "industryPosition": industry_position,
        "concepts": profile_result.get("concepts", []),
        "sources": sources,
        "warnings": list(dict.fromkeys(warnings)),
        "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }


def clamp(value, low=0.0, high=100.0):
    return max(low, min(high, value))


def cache_get(key, ttl_seconds):
    with SECTOR_CACHE_LOCK:
        item = SECTOR_CACHE.get(key)
        if not item or time.time() - item[0] > ttl_seconds:
            return None
        return item[1]


def cache_put(key, value):
    with SECTOR_CACHE_LOCK:
        SECTOR_CACHE[key] = (time.time(), value)
    return value


def amount_number(value):
    value = clean(value)
    if value is None or value == "":
        return None
    raw = str(value).replace(",", "").strip()
    multiplier = 1.0
    if "亿" in raw:
        multiplier = 100000000.0
    elif "万" in raw:
        multiplier = 10000.0
    match = re.search(r"[-+]?\d+(?:\.\d+)?", raw)
    return float(match.group()) * multiplier if match else None


def normalize_sector_name(value):
    value = text(value) or ""
    return re.sub(r"概念|行业|板块|制造业|制造|股份|\s+", "", value).strip().lower()


def board_summary(row, board_type, net_inflow=None, net_ratio=None, activity=50.0):
    change = number(row_value(row, "涨跌幅")) or 0
    leader_change = number(row_value(row, "个股-涨跌幅")) or 0
    score = 50 + change * 8 + leader_change * 1.5 + (activity - 50) * 0.18
    if net_ratio is not None:
        score += clamp(net_ratio * 2.5, -20, 20)
    score = round(clamp(score), 1)
    if score >= 75 and change > 0 and (net_inflow is None or net_inflow > 0):
        status = "主线候选"
    elif score >= 60:
        status = "活跃"
    elif score <= 35:
        status = "偏弱"
    else:
        status = "观察"
    return {
        "id": text(row_value(row, "label")),
        "name": text(row_value(row, "板块")),
        "type": board_type,
        "companyCount": int(number(row_value(row, "公司家数")) or 0),
        "changePercent": change,
        "totalVolume": number(row_value(row, "总成交量")),
        "totalAmount": number(row_value(row, "总成交额")) or 0,
        "netInflow": net_inflow,
        "netInflowRatio": net_ratio,
        "leaderCode": text(row_value(row, "股票代码")),
        "leaderName": text(row_value(row, "股票名称")),
        "leaderPrice": number(row_value(row, "个股-当前价")),
        "leaderChangePercent": leader_change,
        "score": score,
        "status": status,
    }


def load_board_catalog(refresh=False):
    cache_key = "board-catalog"
    if not refresh:
        cached = cache_get(cache_key, 45)
        if cached:
            return cached
    boards = []
    warnings = []
    for indicator, board_type in (("新浪行业", "行业"), ("概念", "概念")):
        try:
            frame = ak.stock_sector_spot(indicator=indicator)
            boards.extend(board_summary(row, board_type) for _, row in frame.iterrows())
        except Exception as exc:
            warnings.append(f"{board_type}板块目录暂不可用: {exc}")
    payload = {
        "available": bool(boards),
        "boards": boards,
        "warnings": warnings,
        "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }
    return cache_put(cache_key, payload)


def search_sectors(keyword, refresh=False):
    normalized_keyword = normalize_sector_name(keyword)
    if not normalized_keyword:
        return {"results": [], "warnings": [], "fetchedAt": dt.datetime.now().isoformat(timespec="seconds")}
    catalog = load_board_catalog(refresh=refresh)
    matches = []
    for board in catalog.get("boards", []):
        normalized_name = normalize_sector_name(board["name"])
        if normalized_keyword not in normalized_name and normalized_name not in normalized_keyword:
            continue
        exact = normalized_name == normalized_keyword
        prefix = normalized_name.startswith(normalized_keyword)
        search_rank = 0 if exact else 1 if prefix else 2
        matches.append((search_rank, -float(board.get("score") or 0), len(normalized_name), board))
    matches.sort(key=lambda item: (item[0], item[1], item[2]))
    return {
        "results": [item[3] for item in matches[:12]],
        "warnings": catalog.get("warnings", []),
        "fetchedAt": catalog.get("fetchedAt"),
    }


def sector_flow_row(flow_frame, sector_name):
    if flow_frame is None or flow_frame.empty:
        return None
    target = normalize_sector_name(sector_name)
    for _, row in flow_frame.iterrows():
        candidate = normalize_sector_name(row_value(row, "行业", "板块"))
        if candidate == target or (len(candidate) >= 2 and (candidate in target or target in candidate)):
            return row
    return None


def load_sector_list(refresh=False):
    cache_key = "sector-list"
    if not refresh:
        cached = cache_get(cache_key, 45)
        if cached:
            return cached

    warnings = []
    sector_frame = ak.stock_sector_spot(indicator="新浪行业")
    flow_frame = None
    try:
        flow_frame = ak.stock_fund_flow_industry(symbol="即时")
    except Exception:
        warnings.append("行业资金流数据源当前超时，资金列暂不参与主线评分")

    amount_values = [number(value) or 0 for value in sector_frame.get("总成交额", [])]
    log_amounts = [math.log10(max(value, 1)) for value in amount_values]
    min_log = min(log_amounts) if log_amounts else 0
    max_log = max(log_amounts) if log_amounts else 1
    sectors = []
    for index, (_, row) in enumerate(sector_frame.iterrows()):
        name = text(row_value(row, "板块"))
        total_amount = number(row_value(row, "总成交额")) or 0
        activity = 50.0 if max_log == min_log else (
            (math.log10(max(total_amount, 1)) - min_log) / (max_log - min_log) * 100
        )
        flow_row = sector_flow_row(flow_frame, name)
        net_inflow = amount_number(row_value(flow_row, "净额", "净流入")) if flow_row is not None else None
        net_ratio = net_inflow / total_amount * 100 if net_inflow is not None and total_amount > 0 else None
        sectors.append(board_summary(
            row, "行业", net_inflow=net_inflow, net_ratio=net_ratio, activity=activity))
    sectors.sort(key=lambda item: item["score"], reverse=True)
    payload = {
        "available": bool(sectors),
        "flowAvailable": any(item["netInflow"] is not None for item in sectors),
        "sectors": sectors,
        "source": "AKShare / 新浪行业行情；同花顺行业资金流（可降级）",
        "warnings": warnings,
        "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }
    return cache_put(cache_key, payload)


def load_sector_members(sector_id, refresh=False):
    if not refresh:
        cached = SECTOR_MEMBER_CACHE.get(sector_id)
        if cached and time.time() - cached[0] <= 45:
            return cached[1].copy()
    frame = ak.stock_sector_detail(sector=sector_id)
    SECTOR_MEMBER_CACHE[sector_id] = (time.time(), frame.copy())
    return frame


def resolve_sector(identifier):
    if str(identifier).lower().startswith("gn_"):
        for sector in load_board_catalog().get("boards", []):
            if sector["id"] == identifier:
                return sector
    listing = load_sector_list().get("sectors", [])
    normalized = normalize_sector_name(identifier)
    for sector in listing:
        if sector["id"] == identifier or sector["name"] == identifier:
            return sector
    for sector in listing:
        candidate = normalize_sector_name(sector["name"])
        if candidate == normalized or (len(candidate) >= 2 and (candidate in normalized or normalized in candidate)):
            return sector
    for sector in load_board_catalog().get("boards", []):
        candidate = normalize_sector_name(sector["name"])
        if sector["id"] == identifier or candidate == normalized:
            return sector
    return None


SECTOR_KEYWORDS = {
    "new_jxhy": ["机械", "机器人", "自动化", "通用设备", "专用设备", "工业母机"],
    "new_yqyb": ["仪器", "仪表"],
    "new_dzqj": ["半导体", "元件", "电子器件", "芯片"],
    "new_dzxx": ["计算机", "软件", "通信", "电子信息", "互联网"],
    "new_dqhy": ["电气", "电器", "电网"],
    "new_fdsb": ["电源", "发电设备", "光伏", "风电"],
    "new_qczz": ["汽车", "汽车零部件"],
    "new_swzz": ["生物", "制药", "医药"],
    "new_ylqx": ["医疗器械", "医疗设备"],
    "new_jrhy": ["银行", "证券", "保险", "金融"],
    "new_ljhy": ["白酒", "酿酒"],
    "new_nlmy": ["农业", "农林牧渔", "养殖"],
    "new_hghy": ["化工", "化学"],
    "new_ysjs": ["有色", "金属"],
    "new_mthy": ["煤炭"],
    "new_syhy": ["石油"],
    "new_dlhy": ["电力"],
    "new_fdc": ["房地产"],
    "new_jzjc": ["建筑", "建材"],
    "new_sphy": ["食品", "饮料"],
    "new_cmyl": ["传媒", "广告", "影视"],
}


def stock_industry_terms(code):
    terms = []
    try:
        frame = ak.stock_industry_change_cninfo(
            symbol=code,
            start_date="20000101",
            end_date=dt.date.today().strftime("%Y%m%d"),
        )
        for column in ("行业门类", "行业次类", "行业大类", "行业中类"):
            if column in frame.columns:
                terms.extend(text(value) for value in frame[column].tail(12) if text(value))
    except Exception:
        pass
    return list(dict.fromkeys(terms))


def resolve_stock_sector(full_code, refresh=False):
    full_code = full_code.lower()
    if not refresh and full_code in STOCK_SECTOR_CACHE:
        return STOCK_SECTOR_CACHE[full_code]
    code = full_code[2:]
    sectors = load_sector_list(refresh=refresh).get("sectors", [])
    terms = stock_industry_terms(code)

    def candidate_score(sector):
        score = 0
        core = normalize_sector_name(sector["name"])
        for term in terms:
            normalized_term = normalize_sector_name(term)
            if core and (core in normalized_term or normalized_term in core):
                score += 100
        for keyword in SECTOR_KEYWORDS.get(sector["id"], []):
            if any(keyword in term for term in terms):
                score += 80
        return score

    ordered = sorted(sectors, key=candidate_score, reverse=True)
    for sector in ordered:
        try:
            frame = load_sector_members(sector["id"], refresh=refresh)
            codes = frame["code"].astype(str).str.zfill(6)
            if (codes == code).any():
                STOCK_SECTOR_CACHE[full_code] = sector
                return sector
        except Exception:
            continue
    return None


def metric_window(frame, minutes):
    if frame.empty:
        return None, None
    latest = number(frame.iloc[-1]["close"])
    reference_index = max(0, len(frame) - minutes - 1)
    reference = number(frame.iloc[reference_index]["close"])
    window = frame.tail(minutes)
    first_price = number(window.iloc[0]["open"])
    high = pd.to_numeric(window["high"], errors="coerce").max()
    low = pd.to_numeric(window["low"], errors="coerce").min()
    momentum = (latest - reference) / reference * 100 if latest is not None and reference else None
    amplitude = (high - low) / first_price * 100 if first_price and pd.notna(high) and pd.notna(low) else None
    return momentum, amplitude


def load_minute_metrics(full_code):
    frame = ak.stock_zh_a_minute(symbol=full_code, period="1", adjust="")
    if frame.empty:
        raise ValueError("暂无分钟行情")
    frame = frame.copy()
    frame["day"] = pd.to_datetime(frame["day"], errors="coerce")
    frame = frame.dropna(subset=["day"]).sort_values("day")
    latest_date = frame.iloc[-1]["day"].date()
    frame = frame[frame["day"].dt.date == latest_date]
    for column in ("open", "high", "low", "close", "volume", "amount"):
        frame[column] = pd.to_numeric(frame[column], errors="coerce")
    return_1m, amplitude_1m = metric_window(frame, 1)
    return_3m, amplitude_3m = metric_window(frame, 3)
    return_5m, amplitude_5m = metric_window(frame, 5)
    recent_count = min(5, len(frame))
    recent_volume = frame.tail(recent_count)["volume"].sum()
    prior = frame.iloc[max(0, len(frame) - recent_count - 10):max(0, len(frame) - recent_count)]["volume"]
    volume_ratio = None
    if not prior.empty and prior.mean() > 0:
        volume_ratio = recent_volume / (prior.mean() * recent_count)
    points = [{
        "time": row["day"].strftime("%H:%M"),
        "price": number(row["close"]),
        "volume": number(row["volume"]),
    } for _, row in frame.tail(36).iterrows()]
    return {
        "return1m": return_1m,
        "return3m": return_3m,
        "return5m": return_5m,
        "amplitude1m": amplitude_1m,
        "amplitude3m": amplitude_3m,
        "amplitude5m": amplitude_5m,
        "volumeRatio": volume_ratio,
        "volumeExpanded": bool(volume_ratio is not None and volume_ratio >= 1.5),
        "points": points,
        "minuteDataTime": frame.iloc[-1]["day"].isoformat(),
    }


def load_stock_flow(full_code):
    url = ("http://money.finance.sina.com.cn/quotes_service/api/json_v2.php/"
           "MoneyFlow.ssl_qsfx_lscjfb?page=1&num=1&sort=opendate&asc=0&daima=" + full_code)
    response = requests.get(url, headers={"User-Agent": "Mozilla/5.0"})
    response.raise_for_status()
    payload = json.loads(response.text)
    if not isinstance(payload, list) or not payload:
        return {}
    row = payload[0]
    return {
        "mainNetInflow": number(row.get("netamount")),
        "mainNetRatio": (number(row.get("ratioamount")) or 0) * 100,
        "flowDate": text(row.get("opendate")),
    }


def select_leader_candidates(frame, selected_code=None):
    candidates = frame.copy()
    for column in ("amount", "mktcap", "changepercent", "turnoverratio", "per"):
        candidates[column] = pd.to_numeric(candidates[column], errors="coerce")
    candidates["_liquidity"] = candidates["amount"].fillna(0).rank(pct=True) * 100
    candidates["_scale"] = candidates["mktcap"].fillna(0).rank(pct=True) * 100
    candidates["_momentum"] = candidates["changepercent"].fillna(0).rank(pct=True) * 100
    candidates["_turnover"] = candidates["turnoverratio"].fillna(0).rank(pct=True) * 100
    candidates["_profit"] = candidates["per"].apply(lambda value: 100 if pd.notna(value) and 0 < value <= 120 else 35 if pd.notna(value) and value > 0 else 0)
    candidates["_leader"] = (
        candidates["_liquidity"] * 0.30
        + candidates["_scale"] * 0.25
        + candidates["_momentum"] * 0.15
        + candidates["_turnover"] * 0.10
        + candidates["_profit"] * 0.20
    )
    ranked = candidates.sort_values("_leader", ascending=False)
    profitable = ranked[ranked["per"] > 0]
    remaining = ranked[~ranked["symbol"].isin(profitable["symbol"])]
    selected = pd.concat([profitable, remaining], ignore_index=True).head(10)
    if selected_code:
        target = candidates[candidates["symbol"].astype(str).str.lower() == selected_code.lower()]
        if not target.empty and target.iloc[0]["symbol"] not in selected["symbol"].values:
            selected = pd.concat([selected.head(9), target.head(1)], ignore_index=True)
    return selected


def component_score(value, scale):
    return 50 if value is None else clamp(50 + value * scale)


def quarter_number(report_date):
    try:
        month = int(str(report_date)[5:7])
        return {3: 1, 6: 2, 9: 3, 12: 4}.get(month)
    except (TypeError, ValueError):
        return None


def to_quarterly_performance(periods):
    by_period = {}
    for period in periods:
        report_date = period.get("reportDate")
        quarter = quarter_number(report_date)
        if report_date and quarter:
            by_period[(str(report_date)[:4], quarter)] = period
    result = []
    for period in periods:
        report_date = period.get("reportDate")
        quarter = quarter_number(report_date)
        if not report_date or not quarter:
            continue
        year = str(report_date)[:4]
        previous = by_period.get((year, quarter - 1)) if quarter > 1 else None

        def single_quarter(field):
            current_value = period.get(field)
            if current_value is None:
                return None
            previous_value = previous.get(field) if previous else None
            return current_value - previous_value if quarter > 1 and previous_value is not None else current_value

        result.append({
            "period": f"{year} Q{quarter}",
            "reportDate": report_date,
            "revenue": single_quarter("revenue"),
            "netProfit": single_quarter("netProfit"),
            "source": period.get("source"),
        })
    return result[:8]


def load_leader_fundamentals(code, full_code):
    cached = LEADER_FUNDAMENTALS_CACHE.get(full_code)
    if cached and time.time() - cached[0] <= 1800:
        return cached[1]
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=2)
    performance_future = executor.submit(load_performance, code, full_code)
    report_future = executor.submit(load_reports, code)
    try:
        performance_result = performance_future.result(timeout=REQUEST_TIMEOUT * 2)
    except Exception:
        performance_result = {"performance": []}
    try:
        report_result = report_future.result(timeout=REQUEST_TIMEOUT * 2)
    except Exception:
        report_result = {"researchReports": []}
    executor.shutdown(wait=False, cancel_futures=True)
    reports = report_result.get("researchReports", [])
    payload = {
        "quarterlyPerformance": to_quarterly_performance(
            performance_result.get("performance", [])),
        "latestReport": reports[0] if reports else None,
    }
    LEADER_FUNDAMENTALS_CACHE[full_code] = (time.time(), payload)
    return payload


def build_leader_reason(row, sector_name, selected):
    reasons = [f"{sector_name}正式成分股"]
    if number(row_value(row, "_scale")) >= 80:
        reasons.append("板块市值前20%")
    if number(row_value(row, "_liquidity")) >= 80:
        reasons.append("板块成交额前20%")
    if number(row_value(row, "_momentum")) >= 80:
        reasons.append("日内涨幅位于板块前20%")
    if number(row_value(row, "_turnover")) >= 80:
        reasons.append("换手活跃度位于板块前20%")
    pe = number(row_value(row, "per"))
    if pe is not None and pe > 0:
        reasons.append("动态PE为正，具备盈利估值基础")
    if selected and len(reasons) == 1:
        reasons.append("所选个股纳入板块强度对照")
    if len(reasons) == 1:
        reasons.append("综合市值、成交与日内强度排名靠前")
    return "；".join(reasons[:4])


def analyze_member(row, selected_code, sector_name):
    full_code = text(row_value(row, "symbol"))
    result = {
        "fullCode": full_code,
        "code": text(row_value(row, "code")),
        "name": text(row_value(row, "name")),
        "currentPrice": number(row_value(row, "trade")),
        "dailyChangePercent": number(row_value(row, "changepercent")),
        "amount": number(row_value(row, "amount")),
        "turnoverRate": number(row_value(row, "turnoverratio")),
        "pe": number(row_value(row, "per")),
        "pb": number(row_value(row, "pb")),
        "marketValue": number(row_value(row, "mktcap")),
        "selected": bool(selected_code and full_code.lower() == selected_code.lower()),
        "warning": None,
    }
    result["leaderReason"] = build_leader_reason(row, sector_name, result["selected"])
    pe = result["pe"]
    result["performanceLabel"] = (
        "盈利估值可用" if pe is not None and 0 < pe <= 120
        else "高估值盈利股" if pe is not None and pe > 120
        else "亏损或暂无PE"
    )
    try:
        result.update(load_minute_metrics(full_code))
    except Exception as exc:
        result["warning"] = f"分钟行情不可用: {exc}"
        result.update({
            "return1m": None, "return3m": None, "return5m": None,
            "amplitude1m": None, "amplitude3m": None, "amplitude5m": None,
            "volumeRatio": None, "volumeExpanded": False, "points": [], "minuteDataTime": None,
        })
    try:
        result.update(load_stock_flow(full_code))
    except Exception:
        result.update({"mainNetInflow": None, "mainNetRatio": None, "flowDate": None})
    try:
        result.update(load_leader_fundamentals(result["code"], full_code))
    except Exception:
        result.update({"quarterlyPerformance": [], "latestReport": None})
    return result


def load_sector_detail(sector_identifier, selected_code=None, refresh=False):
    sector = resolve_sector(sector_identifier)
    if not sector:
        raise ValueError("未找到板块: " + sector_identifier)
    cache_key = f"sector-detail:{sector['id']}:{selected_code or ''}"
    if not refresh:
        cached = cache_get(cache_key, 20)
        if cached:
            return cached
    members = load_sector_members(sector["id"], refresh=refresh)
    selected = select_leader_candidates(members, selected_code)
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=min(10, max(1, len(selected))))
    futures = [
        executor.submit(analyze_member, row, selected_code, sector["name"])
        for _, row in selected.iterrows()
    ]
    stocks = []
    for future in futures:
        try:
            stocks.append(future.result(timeout=REQUEST_TIMEOUT * 3))
        except Exception:
            pass
    executor.shutdown(wait=False, cancel_futures=True)
    available_returns = [item["return5m"] for item in stocks if item.get("return5m") is not None]
    sector_return = sum(available_returns) / len(available_returns) if available_returns else 0
    for item in stocks:
        item["relativeStrength"] = (
            item["return5m"] - sector_return if item.get("return5m") is not None else None
        )
        score = (
            component_score(item.get("return5m"), 8) * 0.25
            + component_score(item.get("return3m"), 10) * 0.20
            + component_score(item.get("return1m"), 14) * 0.10
            + component_score(item.get("relativeStrength"), 10) * 0.15
            + component_score((item.get("volumeRatio") - 1) if item.get("volumeRatio") is not None else None, 35) * 0.15
            + component_score(item.get("mainNetRatio"), 3) * 0.15
        )
        if (item.get("return5m") or 0) < 0 and (item.get("mainNetInflow") or 0) < 0:
            score -= 10
        item["score"] = round(clamp(score), 1)
        signals = []
        if item.get("volumeExpanded"):
            signals.append("放量上攻" if (item.get("return5m") or 0) > 0 else "放量回落")
        if item.get("mainNetInflow") is not None:
            signals.append("资金净流入" if item["mainNetInflow"] > 0 else "资金净流出")
        item["signals"] = signals
    stocks.sort(key=lambda item: item["score"], reverse=True)
    for rank, item in enumerate(stocks, start=1):
        item["rank"] = rank
        item["strengthLabel"] = (
            "板块内分时最强" if rank == 1 and item["score"] >= 65
            else "板块内强势" if rank <= 3 and item["score"] >= 55
            else "板块内偏弱" if item["score"] < 45
            else "板块内中性"
        )
    warnings = []
    missing_minutes = sum(not item.get("points") for item in stocks)
    missing_flow = sum(item.get("mainNetInflow") is None for item in stocks)
    missing_performance = sum(not item.get("quarterlyPerformance") for item in stocks)
    missing_reports = sum(item.get("latestReport") is None for item in stocks)
    if missing_minutes:
        warnings.append(f"{missing_minutes} 只候选股缺少分钟行情")
    if missing_flow:
        warnings.append(f"{missing_flow} 只候选股资金流暂不可用")
    if missing_performance:
        warnings.append(f"{missing_performance} 只候选股近两年季度业绩暂不可用")
    if missing_reports:
        warnings.append(f"{missing_reports} 只候选股近期研报评级暂不可用")
    payload = {
        "available": bool(stocks),
        "sector": sector,
        "selectedCode": selected_code,
        "stocks": stocks,
        "formula": "Top10先按板块成分相关性、成交额、市值、动态PE盈利状态、日内涨幅和换手率筛选；分时强度分=5分钟动量25%+3分钟动量20%+1分钟动量10%+板块相对强度15%+5分钟量比15%+最近交易日主力净流入占比15%。",
        "source": "AKShare / 新浪板块成分与分钟行情、同花顺季度业绩、东方财富研报（可降级）",
        "warnings": warnings,
        "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
    }
    return cache_put(cache_key, payload)


def load_stock_sector_detail(full_code, refresh=False):
    sector = resolve_stock_sector(full_code, refresh=refresh)
    if not sector:
        return {
            "available": False,
            "sector": None,
            "selectedCode": full_code,
            "stocks": [],
            "formula": None,
            "source": "AKShare / 新浪行业",
            "warnings": ["暂时无法识别该股票所属的新浪行业板块"],
            "fetchedAt": dt.datetime.now().isoformat(timespec="seconds"),
        }
    return load_sector_detail(sector["id"], selected_code=full_code, refresh=refresh)


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        path = unquote(parsed.path)
        query = parse_qs(parsed.query)
        refresh = query.get("refresh", ["false"])[0].lower() == "true"
        if path == "/health":
            self.respond(200, {"status": "ok", "provider": "akshare"})
            return
        try:
            if path == "/api/sectors":
                self.respond(200, load_sector_list(refresh=refresh))
                return
            if path == "/api/sectors/search":
                keyword = query.get("keyword", [""])[0]
                self.respond(200, search_sectors(keyword, refresh=refresh))
                return
            stock_sector_match = re.fullmatch(
                r"/api/sectors/stock/((?:sh|sz)\d{6})", path, re.IGNORECASE)
            if stock_sector_match:
                self.respond(200, load_stock_sector_detail(
                    stock_sector_match.group(1).lower(), refresh=refresh))
                return
            sector_match = re.fullmatch(r"/api/sectors/(.+)", path, re.IGNORECASE)
            if sector_match:
                selected = query.get("selected", [None])[0]
                self.respond(200, load_sector_detail(
                    sector_match.group(1), selected_code=selected, refresh=refresh))
                return
            stock_match = re.fullmatch(r"/api/stock/((?:sh|sz)\d{6})", path, re.IGNORECASE)
            if stock_match:
                self.respond(200, build_snapshot(stock_match.group(1).lower()))
                return
            self.respond(404, {"message": "not found"})
        except Exception as exc:
            self.respond(500, {"message": str(exc)})

    def respond(self, status, payload):
        body = json.dumps(
            payload,
            ensure_ascii=False,
            allow_nan=False,
            default=clean,
        ).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt, *args):
        print("%s - %s" % (self.address_string(), fmt % args), flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--request-timeout", type=int, default=7)
    args = parser.parse_args()
    global REQUEST_TIMEOUT
    REQUEST_TIMEOUT = max(3, args.request_timeout)
    socket.setdefaulttimeout(REQUEST_TIMEOUT)
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"AKShare service listening on http://{args.host}:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
