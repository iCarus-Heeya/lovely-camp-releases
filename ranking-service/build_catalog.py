#!/usr/bin/env python3
"""Build the checked-in ranking snapshot used by the Android release.

The snapshot is deliberately generated from public, documented datasets/APIs:
TVmaze supplies show/variety metadata and IMDb's public datasets supply movie
ratings.  This script does not collect cookies, protected pages, playback
URLs, or private API credentials.  A later hosted aggregator can replace the
asset without changing the client contract.
"""

from __future__ import annotations

import csv
import gzip
import html
import json
import math
import re
import sys
import time
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable
from urllib.parse import quote, urlencode, urlparse
from urllib.request import Request, urlopen

csv.field_size_limit(10 * 1024 * 1024)


ROOT = Path(__file__).resolve().parents[2]
TMP = ROOT / "tmp"
OUT = ROOT / "source" / "app" / "src" / "main" / "assets" / "ranking"
OUT_FILE = OUT / "catalog.json"
OUT_INDEX_FILE = OUT / "catalog-index.json"
OUT_ITEMS_FILE = OUT / "items.ndjson"
POSTER_DIR = OUT / "posters"
WIKIDATA_COUNTRY_CACHE = TMP / "wikidata_country_index.json"
TVMAZE_INDEX_CACHE = TMP / "tvmaze_shows_index.json"
TODAY = datetime.now(timezone.utc).strftime("%Y-%m-%d")
DATA_AS_OF = f"{TODAY}T00:00:00Z"
WIKIDATA_ENDPOINT = "https://query.wikidata.org/sparql"
# The public SPARQL endpoint accepts a few hundred IMDb identifiers per
# VALUES clause.  A larger batch keeps the complete-catalog metadata pass
# bounded while retaining the existing small worker pool/rate limit.
WIKIDATA_BATCH_SIZE = 500
# TVmaze's public index is paginated by numeric show id.  The current public
# index ends at page 379; scanning that complete range avoids the old 15-page
# top-slice which silently dropped most regional and recent series.  A small
# extra scan ceiling is intentionally not used: a 404 page is not a catalogue
# row and repeatedly probing beyond the known end only adds rate-limit noise.
TVMAZE_PAGE_COUNT = 380
TVMAZE_CACHE_MAX_AGE_SECONDS = 24 * 60 * 60
# Keep the complete useful public-rating slice instead of a high-score/top-N
# sample.  One hundred votes is the minimum evidence threshold: rows below it
# are not stable enough to call a public ranking, while the old 1,000-vote
# floor hid too much recent Chinese and regional catalogue coverage.
MIN_PUBLIC_VOTES = 100
CATALOG_VERSION = "2026-10-07.2"
IDENTITY_MAP_VERSION = "imdb-wikidata-p495-akas-tvmaze-wikipedia-zh-v12"
METHODOLOGY_VERSION = "imdb-rated-scope-v7-preferred-genres-tvmaze-full-index"
RANKING_PRIOR_MEAN = 7.0
RANKING_PRIOR_VOTES = 1000.0
PREFERRED_RANKING_GENRES = ("喜剧", "动作", "恐怖", "悬疑", "惊悚")

# Board ids are part of the checked-in catalog contract.  Keep the labels in
# the Android capability payload in Chinese, while retaining the provider id
# in the id for deterministic filtering and auditability.
RANKING_BOARD_IDS = ("aggregate", "source-imdb", "source-tvmaze")

GENRE_LABELS = {
    "Action": "动作", "Adventure": "冒险", "Animation": "动画", "Comedy": "喜剧",
    "Crime": "犯罪", "Documentary": "纪录", "Drama": "剧情", "Family": "家庭",
    "Fantasy": "奇幻", "Food": "美食", "History": "历史", "Horror": "恐怖",
    "Music": "音乐", "Mystery": "悬疑", "Reality": "真人秀", "Romance": "爱情",
    "Science-Fiction": "科幻", "Sport": "体育", "Thriller": "惊悚",
    "Travel": "旅行", "War": "战争", "Western": "西部", "Talk Show": "脱口秀",
    "Game Show": "游戏节目", "Home and Garden": "生活", "News": "新闻",
    "Anime": "动画", "Biography": "传记", "Children": "少儿", "Legal": "律政",
    "Medical": "医疗", "Nature": "自然", "Sci-Fi": "科幻", "Supernatural": "超自然",
    "Soap": "肥皂剧", "Special Interest": "专题", "Sports": "体育",
    "Espionage": "谍战", "Travel": "旅行", "Reality-TV": "真人秀",
    "Game-Show": "游戏节目", "Talk-Show": "脱口秀", "TV Movie": "电视电影",
    "Musical": "音乐剧", "Film-Noir": "黑色电影", "Adult": "成人",
    "Short": "短片",
}
VARIETY_GENRES = {"Reality", "Game Show", "Talk Show", "Music", "Food", "Home and Garden", "News"}
REGION_LABELS = {
    "US": "美国", "GB": "英国", "JP": "日本", "KR": "韩国", "CN": "中国",
    "TH": "泰国", "TW": "中国台湾", "HK": "中国香港", "CA": "加拿大", "AU": "澳大利亚",
    "FR": "法国", "DE": "德国", "IN": "印度", "ES": "西班牙", "IT": "意大利",
    "BR": "巴西", "NZ": "新西兰", "IE": "爱尔兰", "MX": "墨西哥", "ID": "印度尼西亚",
}

REGIONAL_SEED_QUERIES = (
    # TVmaze's global index contains these shows, but its top-weight slice can
    # otherwise hide Asian catalogues behind older US/GB titles.
    "The Untamed", "Nirvana in Fire", "The Longest Day in Chang'an", "Joy of Life",
    "The Knockout", "Reset", "The Long Season", "The Double", "The Story of Ming Lan",
    "Squid Game", "Crash Landing on You", "Reply 1988", "My Mister", "Moving",
    "The Glory", "Kingdom", "My Liberation Notes", "Lovely Runner", "Alchemy of Souls",
    "2gether: The Series", "Bad Buddy", "Not Me", "I Told Sunset About You",
    "Moonlight Chicken", "Last Twilight", "Girl From Nowhere", "KinnPorsche", "Love by Chance", "Go Fighting!",
    "Hurry Up, Brother", "The Rap of China", "Street Dance of China", "The Big Band",
    "Call Me by Fire", "The Face Thailand",
)

VARIETY_TITLE_HINTS = {
    "Go Fighting!", "Hurry Up, Brother", "The Rap of China", "Street Dance of China",
    "The Big Band", "Call Me by Fire", "The Face Thailand",
}

# IMDb's all-time weighted top list is heavily anglophone. Keep a small,
# stable regional film tranche so country filters do not return an empty result
# for Chinese, Korean, or Thai cinema. These IDs are still scored and
# described from the same public IMDb/Wikipedia pipeline; the map only
# controls inclusion and the Chinese display title.
REGIONAL_MOVIE_SEEDS = {
    "CN": (
        "tt0092263", "tt0101640", "tt0106332", "tt0110081", "tt0190332",
        "tt0299977", "tt0338564", "tt0425637", "tt1326972", "tt8637428", "tt9586294",
    ),
    "KR": (
        "tt0364569", "tt1190539", "tt12477480", "tt4016934", "tt5215952",
        "tt5700672", "tt7282468", "tt10633456",
    ),
    "TH": ("tt0368909", "tt0387898", "tt0440803", "tt1588895", "tt6788942"),
}

REGIONAL_MOVIE_TITLES = {
    "tt0092263": "英雄本色", "tt0101640": "大红灯笼高高挂", "tt0106332": "霸王别姬",
    "tt0110081": "活着", "tt0190332": "卧虎藏龙", "tt0299977": "英雄",
    "tt0338564": "无间道", "tt0425637": "赤壁", "tt1326972": "赤壁2：决战天下",
    "tt8637428": "别告诉她", "tt9586294": "少年的你", "tt0364569": "老男孩",
    "tt1190539": "追击者", "tt12477480": "分手的决心", "tt4016934": "下女",
    "tt5215952": "哭声", "tt5700672": "釜山行", "tt7282468": "燃烧",
    "tt10633456": "米纳里", "tt0368909": "拳霸", "tt0387898": "热带疾病",
    "tt0440803": "鬼影", "tt1588895": "波米叔叔的前世今生", "tt6788942": "天才枪手",
}


def ranking_board_ids() -> tuple[str, ...]:
    """Return the source/aggregate boards exposed by this catalog."""
    return RANKING_BOARD_IDS


def ranking_score_for_board(item: dict, board_id: str) -> float | None:
    """Read the score for a declared board without inventing missing data.

    The aggregate board is the only board allowed to use ``aggregateScore``.
    Source boards require a matching ``sourceRatings`` row, so an IMDb-only
    item never appears on the TVmaze board and vice versa.
    """
    if board_id == "aggregate":
        value = item.get("aggregateScore")
        return float(value) if isinstance(value, (int, float)) else None
    if not board_id.startswith("source-"):
        return None
    source_id = board_id.removeprefix("source-")
    for rating in item.get("sourceRatings") or []:
        if not isinstance(rating, dict) or rating.get("sourceId") != source_id:
            continue
        value = rating.get("value")
        if isinstance(value, (int, float)):
            return float(value)
    return None


def ranking_bayesian_score(
    score: float,
    votes: int,
    prior_mean: float = RANKING_PRIOR_MEAN,
    prior_votes: float = RANKING_PRIOR_VOTES,
) -> float:
    """Use votes as confidence toward a neutral prior, never as amplification."""
    if votes <= 0 or prior_votes <= 0:
        return score
    confidence = votes / (votes + prior_votes)
    return max(0.0, min(10.0, score * confidence + prior_mean * (1.0 - confidence)))

MOVIE_COUNTRY_BY_TITLE = {
    "The Shawshank Redemption": ["US"], "The Godfather": ["US"],
    "The Dark Knight": ["US"], "12 Angry Men": ["US"], "The Godfather Part II": ["US"],
    "Schindler's List": ["US"], "Forrest Gump": ["US"], "Pulp Fiction": ["US"],
    "Fight Club": ["US"], "Inception": ["US"], "Star Wars: Episode V - The Empire Strikes Back": ["US"],
    "Star Wars": ["US"], "GoodFellas": ["US"], "The Matrix": ["US"], "Interstellar": ["US"],
    "One Flew Over the Cuckoo's Nest": ["US"], "The Silence of the Lambs": ["US"],
    "Terminator 2: Judgment Day": ["US"], "Se7en": ["US"], "The Green Mile": ["US"],
    "Saving Private Ryan": ["US"], "Back to the Future": ["US"], "The Lion King": ["US"],
    "American History X": ["US"], "Gladiator": ["US"], "The Departed": ["US"],
    "The Prestige": ["US"], "Django Unchained": ["US"], "Spider-Man: Across the Spider-Verse": ["US"],
    "The Dark Knight Rises": ["US"], "Dune: Part Two": ["US"], "Coco": ["US"],
    "Avengers: Infinity War": ["US"], "Avengers: Endgame": ["US"],
    "Spider-Man: Into the Spider-Verse": ["US"], "Joker": ["US"], "Oppenheimer": ["US"],
    "Top Gun: Maverick": ["US"], "Green Book": ["US"], "1917": ["GB"],
    "Spider-Man: No Way Home": ["US"], "Hacksaw Ridge": ["US"], "Logan": ["US"],
    "Deadpool": ["US"], "The Odyssey": ["US"], "Project Hail Mary": ["US"],
    "The Lord of the Rings: The Return of the King": ["NZ"],
    "The Lord of the Rings: The Fellowship of the Ring": ["NZ"],
    "The Lord of the Rings: The Two Towers": ["NZ"],
    "Il buono, il brutto, il cattivo": ["IT"], "Sen to Chihiro no kamikakushi": ["JP"],
    "Kimi no na.": ["JP"], "Kimi no na wa.": ["JP"], "Satluj": ["IN"], "12th Fail": ["IN"],
    "Cidade de Deus": ["BR"], "Soorarai Pottru": ["IN"],
    "Jai Bhim": ["IN"], "The Kashmir Files": ["IN"], "Gisaengchung": ["KR"],
    "Parasite": ["KR"],
}

# TVmaze occasionally omits network.country for otherwise well-known titles.
# Keep a small deterministic identity map so the country filter does not lose
# recognizable entries merely because one upstream field is absent.
SHOW_COUNTRY_BY_TITLE = {
    "Stranger Things": ["US"],
    "The Crown": ["GB"],
    "Lucifer": ["US"],
    "Goliath": ["US"],
    "Kingdom": ["KR"],
    "Moving": ["KR"],
    "I Told Sunset About You": ["TH"],
    "Chef's Table": ["US"],
    "The Get Down": ["US"],
    "Mozart in the Jungle": ["US"],
    "The Rap of China": ["CN"],
    "The Big Band": ["CN"],
}

# The public feeds are intentionally kept as source metadata, but their
# display names are not suitable for a Chinese-first product.  These are
# stable, editorially reviewed names for the high-visibility titles that can
# otherwise arrive from IMDb/TVmaze without a Chinese AKA.  They are not used
# as search aliases only; the original source name is retained separately.
LOCALIZED_TITLE_OVERRIDES = {
    # Films
    "The Shawshank Redemption": "肖申克的救赎", "The Godfather": "教父",
    "The Dark Knight": "蝙蝠侠：黑暗骑士", "12 Angry Men": "十二怒汉",
    "The Godfather Part II": "教父2", "Schindler's List": "辛德勒的名单",
    "The Lord of the Rings: The Return of the King": "指环王3：王者归来",
    "The Lord of the Rings: The Fellowship of the Ring": "指环王1：护戒使者",
    "The Good, the Bad and the Ugly": "黄金三镖客", "Forrest Gump": "阿甘正传",
    "Pulp Fiction": "低俗小说", "Fight Club": "搏击俱乐部",
    "The Lord of the Rings: The Two Towers": "指环王2：双塔奇兵", "Inception": "盗梦空间",
    "Star Wars: Episode V - The Empire Strikes Back": "星球大战5：帝国反击战",
    "GoodFellas": "好家伙", "The Matrix": "黑客帝国", "Interstellar": "星际穿越",
    "12th Fail": "十二年级的失败", "One Flew Over the Cuckoo's Nest": "飞越疯人院",
    "Star Wars: Episode IV - A New Hope": "星球大战4：新希望", "The Silence of the Lambs": "沉默的羔羊",
    "Terminator 2: Judgment Day": "终结者2：审判日", "Seven": "七宗罪", "Se7en": "七宗罪",
    "The Green Mile": "绿里奇迹", "Saving Private Ryan": "拯救大兵瑞恩", "Spirited Away": "千与千寻",
    "City of God": "上帝之城", "Soorarai Pottru": "飞行员", "Jai Bhim": "杰伊·比姆",
    "Back to the Future": "回到未来", "The Lion King": "狮子王", "Léon: The Professional": "这个杀手不太冷",
    "American History X": "美国历史档案", "Gladiator": "角斗士", "The Departed": "无间道风云",
    "The Prestige": "致命魔术", "Django Unchained": "被解救的姜戈", "The Kashmir Files": "克什米尔档案",
    "The Dark Knight Rises": "蝙蝠侠：黑暗骑士崛起", "Dune: Part Two": "沙丘2", "Coco": "寻梦环游记",
    "The Odyssey": "奥德赛", "Avengers: Infinity War": "复仇者联盟3：无限战争",
    "Avengers: Endgame": "复仇者联盟4：终局之战", "Spider-Man: Across the Spider-Verse": "蜘蛛侠：纵横宇宙",
    "Spider-Man: Into the Spider-Verse": "蜘蛛侠：平行宇宙", "Your Name.": "你的名字。", "Joker": "小丑",
    "Project Hail Mary": "挽救计划", "Oppenheimer": "奥本海默", "Top Gun: Maverick": "壮志凌云2：独行侠",
    "Green Book": "绿皮书", "Spider-Man: No Way Home": "蜘蛛侠：英雄无归", "Hacksaw Ridge": "血战钢锯岭",
    "Logan": "金刚狼3：殊死一战", "Deadpool": "死侍", "The Good, the Bad and the Ugly": "黄金三镖客",
    "Punjab '95": "旁遮普95", "1917": "1917", "Gisaengchung": "寄生虫", "Parasite": "寄生虫",
    # Series and animation
    "Breaking Bad": "绝命毒师", "Band of Brothers": "兄弟连", "Planet Earth": "地球脉动",
    "The World at War": "战争年代", "The Wire": "火线", "Avatar: The Last Airbender": "降世神通：最后的气宗",
    "Game of Thrones": "权力的游戏", "Lawman": "执法者", "Holocaust": "大屠杀", "The Sopranos": "黑道家族",
    "Sherlock": "神探夏洛克", "Attack on Titan": "进击的巨人", "One Piece": "海贼王", "Death Note": "死亡笔记",
    "Only Fools and Horses....": "只有傻瓜和马", "Hunter x Hunter": "全职猎人", "Fawlty Towers": "弗尔蒂旅馆",
    "Cowboy Bebop": "星际牛仔", "Downton Abbey": "唐顿庄园", "Line of Duty": "重任在肩",
    "Steins;Gate": "命运石之门", "Taskmaster": "王牌任务", "Answer Me 1988": "请回答1988",
    "My Mister": "我的大叔", "Naruto: Shippūden": "火影忍者疾风传", "Fullmetal Alchemist: Brotherhood": "钢之炼金术师：兄弟会",
    "Stranger Things": "怪奇物语", "The Crown": "王冠", "Crash Landing on You": "爱的迫降",
    "Westworld": "西部世界", "Taboo": "禁忌", "Snowfall": "白粉飞", "Big Little Lies": "大小谎言",
    "Billions": "亿万", "Healer": "治愈者", "War & Peace": "战争与和平", "Lucifer": "路西法",
    "Goliath": "大法官", "The Night Manager": "夜班经理", "City Hunter": "城市猎人", "Moving": "超异能族",
    "Secret Love": "秘密", "Kill Me, Heal Me": "杀了我治愈我", "BrainDead": "副总统",
    "The Good Place": "善地", "The Greatest Marriage": "最佳婚姻", "Bad Buddy": "两小无拆",
    "Preacher": "传教士", "KinnPorsche": "黑帮少爷爱上我", "2gether The Series": "假偶天成",
    "Love Sick: The Series": "为爱所困", "ThirTEEN Terrors": "十三骇人游戏", "The Longest Day in Chang'An": "长安十二时辰",
    "Empresses in the Palace": "甄嬛传", "Guai Xia Yi Zhi Mei": "怪侠一枝梅", "Full House": "浪漫满屋",
    "The Empress of China": "武媚娘传奇", "The Untamed": "陈情令", "Nirvana in Fire": "琅琊榜",
    "Joy of Life": "庆余年", "Reset": "开端", "The Glory": "黑暗荣耀", "Kingdom": "王国",
    "The Long Season": "漫长的季节", "The Double": "墨雨云间",
    "Steel Ball Run: JoJo's Bizarre Adventure": "JOJO的奇妙冒险：钢球重跑",
    "The Story of Ming Lan": "知否知否应是绿肥红瘦", "My Liberation Notes": "我的解放日志",
    "Lovely Runner": "背着善宰跑", "Alchemy of Souls": "还魂", "Not Me": "无可替代",
    "I Told Sunset About You": "以你的心诠释我的爱", "Moonlight Chicken": "月光鸡饭",
    "Last Twilight": "不曾遗忘的暮色",
    # IMDb's Korean originals can be paired with Japanese-script release
    # aliases. Keep these reviewed Chinese names keyed to stable source IDs.
    "Seuwiteuhom": "甜蜜家园", "Jomyeonggage": "照明店的客人们",
    # Variety and factual programmes
    "The Great British Bake Off": "英国烘焙大赛", "Flight of the Conchords": "飞行乐团",
    "Dr. Horrible's Sing-Along Blog": "恐怖博士的欢唱博客", "Later... with Jools Holland": "朱尔斯·霍兰德晚间秀",
    "Chef's Table": "主厨的餐桌", "Good Eats": "好吃", "The Mighty Boosh": "巨蟒剧团", "Galavant": "游侠笑传",
    "Mozart in the Jungle": "丛林中的莫扎特", "Kids Baking Championship": "少儿烘焙大赛",
    "Anthony Bourdain: No Reservations": "安东尼·波登：未知之旅", "Great British Menu": "英国美食大赛",
    "Anthony Bourdain: Parts Unknown": "波登不设限", "Nashville": "音乐之乡", "American Masters": "美国大师",
    "Crazy Ex-Girlfriend": "疯狂前女友", "Beavis and Butt-Head": "瘪四与大头蛋", "Kitchen Nightmares": "厨房噩梦",
    "Young & Hungry": "少年食客", "Top Chef": "顶级厨师", "Bar Rescue": "酒吧救援",
    "Saturday Kitchen Live": "周六厨房直播", "Diners, Drive-Ins and Dives": "餐车探秘", "Food Unwrapped": "食物揭秘",
    "Feed the Beast": "喂养野兽", "MasterChef Australia": "澳洲顶级厨师", "Empire": "嘻哈帝国",
    "Hell's Kitchen": "地狱厨房", "Bizarre Foods with Andrew Zimmern": "安德鲁·齐默恩奇异食物",
    "MasterChef": "厨艺大师", "Star": "明星", "Jamie\'s Super Food": "杰米的超级食物", "Dream High": "梦想起飞",
    "MasterChef Junior": "少年厨艺大师", "Cutthroat Kitchen": "残酷的厨房", "So You Think You Can Dance": "舞林争霸",
    "Glee": "欢乐合唱团", "The Voice": "美国之声", "The X Factor": "英国歌唱选秀", "Chopped": "铁人料理",
    "Cake Masters": "蛋糕大师", "The Emperor's Cook": "天皇的御厨", "Late Night Restaurant": "深夜食堂",
    "ATM 2 Romance Error": "恋爱错误2", "Treme": "特雷梅", "The Get Down": "少年嘻哈梦",
    "Garfunkel and Oates": "加芬克尔与奥茨", "Best Time Ever with Neil Patrick Harris": "尼尔·帕特里克·哈里斯的最佳时光",
    "Vinyl": "黑胶", "The Partridge Family": "鹧鸪家庭", "Love & Hip Hop: Atlanta": "爱与嘻哈：亚特兰大",
    "Ramsay's Costa Del Nightmares": "戈登拉姆齐的地狱餐厅", "Talia in the Kitchen": "厨房里的塔莉娅",
    "The Great British Bake Off: An Extra Slice": "英国烘焙大赛：加餐", "Austin & Ally": "奥斯汀与艾莉",
    "Lip Sync Battle": "假唱大赛", "Ramsay's Kitchen Nightmares": "戈登拉姆齐的厨房噩梦", "JONAS": "乔纳斯",
    "Sonny with a Chance": "桑尼明星梦", "The Great Food Truck Race": "美食卡车大赛", "Still The King": "仍是国王",
    "MasterChef Canada": "加拿大厨艺大师", "Bizarre Foods: Delicious Destinations": "奇异美食目的地",
    "Persevere, Goo Hae Ra": "七颠八起具海拉",
    "Go Fighting!": "极限挑战", "Hurry Up, Brother": "奔跑吧兄弟",
    "The Rap of China": "中国有嘻哈", "Street Dance of China": "这就是街舞",
    "The Big Band": "乐队的夏天", "Call Me by Fire": "披荆斩棘",
    "The Face Thailand": "泰国超模",
}

# A small reviewed map closes the identity gap for current titles whose public
# AKA rows have no Chinese label yet.  The synopsis/poster values are public
# Wikipedia metadata and are kept by IMDb id so they cannot be applied to a
# homonymous title accidentally.
IMDB_METADATA_OVERRIDES = {
    "tt11612120": {
        "title": "甜蜜家园",
    },
    "tt31014115": {
        "title": "照明店的客人们",
    },
    "tt38268282": {
        "title": "JOJO的奇妙冒险：钢球重跑",
        "summary": "《JOJO的奇妙冒险：钢球重跑》是荒木飞吕彦创作的《JOJO的奇妙冒险》第七部故事，背景设定在1890年的美国，围绕乔尼·乔斯达与杰洛·齐贝林参加横贯大陆的钢球赛展开。",
        "poster": "https://upload.wikimedia.org/wikipedia/en/8/87/Steel_Ball_Run_1.jpg?utm_source=en.wikipedia.org&utm_campaign=api&utm_content=thumbnail_unscaled",
    },
    "tt26471411": {
        "title": "苦尽柑来遇见你",
        "summary": "《苦尽柑来遇见你》是2025年韩国爱情生活剧，讲述在济州岛出生的爱纯与宽植从少年到暮年的一生，剧集于2025年3月在Netflix上线。",
        "poster": "https://upload.wikimedia.org/wikipedia/en/e/e8/When_Life_Gives_You_Tangerines_poster.png?utm_source=en.wikipedia.org&utm_campaign=api&utm_content=thumbnail_unscaled",
    },
    "tt2788316": {
        "title": "幕府将军",
        "summary": "《幕府将军》是以17世纪日本为背景的历史剧，讲述领主吉井虎永与航海家约翰·布莱克索恩在战国权力变化中的命运交汇。",
        "poster": "https://upload.wikimedia.org/wikipedia/en/b/b6/Sh%C5%8Dgun_%282024_miniseries%29_poster.jpg?utm_source=en.wikipedia.org&utm_campaign=api&utm_content=thumbnail_unscaled",
    },
    "tt34071601": {
        "title": "路过我年少时光的蓝色",
        "summary": "《路过我年少时光的蓝色》是2024年中国青春爱情短剧，围绕艺术少年齐路与秦晓在成长、家庭和自我认同中的相遇展开。",
        "poster": "https://static.tvmaze.com/uploads/images/original_untouched/530/1326974.jpg",
    },
    # IMDb/Wikidata identify this 2025 co-production as CN/US/GB but expose
    # no stable Chinese AKA.  Keep the UI Chinese-first while marking the
    # label as a translation rather than pretending it is an official title.
    "tt34909040": {
        "title": "约会游戏（暂译）",
    },
    # TVmaze supplies the reviewed poster for this Thai series.  The iQIYI
    # Chinese catalogue and Baidu Chinese entry both use “永远的逝友”; keep
    # that stable simplified title while retaining the source title/search id.
    "tt30581662": {
        "title": "永远的逝友",
        "poster": "https://static.tvmaze.com/uploads/images/original_untouched/484/1210725.jpg",
    },
}

# A small, explicit bridge for newly announced Chinese productions whose
# Wikidata country-of-origin claim has not landed yet.  These are not inferred
# from a Chinese release market; they are reviewed title identities with a
# stable Chinese production name and are kept separate from the broad AKA
# market list.
RECENT_PRODUCTION_COUNTRY_OVERRIDES = {
    "Zhu yu": ["CN"],
    "Chang'an er shi si ji": ["CN"],
}


def has_cjk(value: str) -> bool:
    return any("\u3400" <= char <= "\u9fff" for char in value)


def has_japanese_script(value: str) -> bool:
    """Return whether a label contains hiragana or katakana."""
    return any("\u3040" <= char <= "\u30ff" for char in value)


def has_hangul_script(value: str) -> bool:
    """Return whether a label contains Korean Hangul syllables or jamo."""
    return any(
        "\u1100" <= char <= "\u11ff"
        or "\u3130" <= char <= "\u318f"
        or "\uac00" <= char <= "\ud7af"
        for char in value
    )


def is_chinese_title_candidate(value: str) -> bool:
    """Treat Han-only (plus normal punctuation/Latin brands) labels as Chinese."""
    return has_cjk(value) and not has_japanese_script(value) and not has_hangul_script(value)


def metadata_coverage(items: list[dict]) -> dict[str, int]:
    """Return conservative coverage counts for the fields the Chinese UI shows.

    A generated fallback sentence is intentionally not counted as a synopsis,
    and a non-Chinese title is not counted as a localized identity.  The
    counts are shipped in the compact catalog index so the app can disclose
    the data boundary without loading the full JSON catalogue.
    """
    counts = {
        "total": len(items),
        "chineseTitle": 0,
        "summary": 0,
        "poster": 0,
        "region": 0,
        "rating": 0,
    }
    for item in items:
        title = clean_text(str(item.get("displayTitle") or ""))
        if is_chinese_title_candidate(title):
            counts["chineseTitle"] += 1
        summary = clean_text(str(item.get("summary") or ""))
        if summary and not summary.startswith("公开资料概览") and has_cjk(summary):
            counts["summary"] += 1
        if clean_text(str(item.get("posterUrl") or "")):
            counts["poster"] += 1
        if item.get("countries"):
            counts["region"] += 1
        ratings = item.get("sourceRatings") or []
        if item.get("aggregateScore") is not None or any(rating.get("value") is not None for rating in ratings):
            counts["rating"] += 1
    return counts


def extract_chinese_title(value: str | None) -> str | None:
    """Return the Chinese side of a bilingual title, if one is present.

    IMDb/Wikidata/TVmaze occasionally emit labels such as ``English / 中文``
    or ``中文 (English)``.  Treating the whole label as a localized title
    would still leak English into the main card, so keep the CJK-bearing side
    while leaving the original value available through ``originalTitle`` and
    ``aliases`` for search/auditability.
    """
    cleaned = clean_text(value)
    if not is_chinese_title_candidate(cleaned):
        return None

    parts = [part.strip() for part in re.split(r"\s*(?:/|／|\||｜)\s*", cleaned) if part.strip()]
    for part in parts:
        bracketed = re.findall(r"[\(（\[]([^\)）\]]+)[\)）\]]", part)
        cjk_bracketed = [candidate.strip() for candidate in bracketed if is_chinese_title_candidate(candidate)]
        outside = re.sub(r"[\(（\[][^\)）\]]*[\)）\]]", "", part).strip()
        if cjk_bracketed and not is_chinese_title_candidate(outside):
            return cjk_bracketed[0]

        # Remove an English-only disambiguation suffix, e.g. ``绝命毒师
        # (Breaking Bad)``.  Chinese punctuation/brand text is preserved.
        without_english_suffix = re.sub(
            r"\s*[\(（\[][^\)）\]]*[\)）\]]\s*$",
            "",
            part,
        ).strip()
        if is_chinese_title_candidate(without_english_suffix):
            return without_english_suffix
        if is_chinese_title_candidate(part):
            return part
    return None


def choose_localized_title(primary: str, candidates: Iterable[str]) -> str:
    """Choose a Chinese display title while preserving the source title elsewhere."""
    for candidate in candidates:
        cleaned = clean_text(str(candidate))
        localized = extract_chinese_title(cleaned)
        if localized and localized != primary:
            return localized
    return primary


def preferred_localized_title(primary: str, candidates: Iterable[str] = ()) -> str:
    """Apply the curated Chinese map before making a network lookup."""
    override = LOCALIZED_TITLE_OVERRIDES.get(primary)
    return override or choose_localized_title(primary, candidates)


def chinese_summary_or_fallback(
    summary: str | None,
    display_title: str,
    *,
    year: int | None = None,
    countries: Iterable[str] = (),
    genres: Iterable[str] = (),
) -> str:
    """Never leak an English synopsis into the Chinese-first client surface.

    The upstream feeds are allowed to omit a localized synopsis.  Returning a
    deterministic metadata sentence is more useful than exposing an English
    paragraph or an empty card, while keeping the limitation explicit.
    """
    cleaned = clean_text(summary)
    if cleaned and has_cjk(cleaned) and not any(
        marker in cleaned for marker in ("公开简介暂缺", "公开剧情简介暂缺", "剧情简介暂缺")
    ):
        return cleaned

    context: list[str] = []
    if isinstance(year, int):
        context.append(f"{year}年")
    country_labels = [
        REGION_LABELS.get(str(code), str(code))
        for code in countries
        if REGION_LABELS.get(str(code), str(code))
    ]
    if country_labels:
        context.append("、".join(country_labels))
    genre_labels = [clean_text(str(value)) for value in genres if clean_text(str(value))]
    if genre_labels:
        context.append("、".join(genre_labels[:2]))
    if context:
        return f"公开资料概览：《{display_title}》是{''.join(context)}作品；当前快照未收录剧情正文。"
    return f"公开资料概览：《{display_title}》；当前快照未收录剧情正文。"


def show_region(show: dict) -> str | None:
    channel = show.get("network") or show.get("webChannel") or {}
    country = channel.get("country") or {}
    code = country.get("code")
    return code if isinstance(code, str) and code in REGION_LABELS else None


def show_country_codes(show: dict) -> list[str]:
    """Return source country codes, with a reviewed title fallback."""
    region = show_region(show)
    if region:
        return [region]
    title = clean_text(show.get("name"), "")
    return list(SHOW_COUNTRY_BY_TITLE.get(title, []))


def show_year(show: dict) -> int | None:
    premiered = show.get("premiered") or ""
    return int(premiered[:4]) if str(premiered)[:4].isdigit() else None


def select_stratified_tv_candidates(
    candidates: list[tuple[float, dict]],
    media_type: str,
    limit: int = 64,
) -> list[dict]:
    """Keep recent and regional shows before filling by the weighted score."""
    ordered = [
        item
        for _, item in sorted(
            (pair for pair in candidates if pair[1].get("media") == media_type),
            key=lambda pair: (-pair[0], pair[1]["show"].get("id", 0)),
        )
    ]
    selected: list[dict] = []
    selected_ids: set[int] = set()

    def add_where(predicate, quota: int | None = None) -> None:
        added = 0
        for item in ordered:
            show_id = item["show"].get("id")
            if show_id in selected_ids or not predicate(item):
                continue
            selected.append(item)
            selected_ids.add(show_id)
            added += 1
            if len(selected) >= limit or (quota is not None and added >= quota):
                return

    for region in ("CN", "KR", "TH", "JP", "GB", "US"):
        add_where(lambda item, region=region: show_region(item["show"]) == region, quota=8)
    add_where(lambda item: (show_year(item["show"]) or 0) >= 2016, quota=20)
    add_where(
        lambda item: 2010 <= (show_year(item["show"]) or 0) <= 2015,
        quota=12,
    )
    add_where(lambda item: True)
    return selected[:limit]


def fetch_json(url: str, timeout: int = 12):
    request = Request(url, headers={"User-Agent": "lovely-camp-ranking-builder/1.0"})
    with urlopen(request, timeout=timeout) as response:
        return json.load(response)


def wikidata_index(
    title_ids: set[str],
    query_missing_ids: set[str] | None = None,
) -> dict[str, dict[str, object]]:
    """Resolve production countries and Chinese metadata by IMDb id.

    IMDb's ``title.akas`` contains release/alias markets, not the production
    country.  Treating those rows as countries is what made a Japanese or US
    title appear under the China filter.  Wikidata links IMDb ids to the
    structured country-of-origin claim (P495), and also provides a Chinese
    label/description for many entries.  The result is cached outside the
    repository so repeat catalog builds do not re-query the public endpoint.
    """
    if not title_ids:
        return {}
    cached: dict[str, dict[str, object]] = {}
    try:
        raw = json.loads(WIKIDATA_COUNTRY_CACHE.read_text(encoding="utf-8"))
        if isinstance(raw, dict):
            cached = {str(key): value for key, value in raw.items() if isinstance(value, dict)}
    except Exception:
        cached = {}

    # Keep already cached historical identities available, but query every
    # missing title in the declared catalogue scope.  Restricting new
    # lookups to recent rows made the long tail permanently English/unknown
    # even though Wikidata exposes a Chinese label or production country.
    # Callers may still pass a narrower scope for explicitly bounded probes.
    query_scope = title_ids if query_missing_ids is None else query_missing_ids.intersection(title_ids)
    missing = sorted(title_id for title_id in query_scope if wikidata_cache_needs_refresh(cached.get(title_id)))

    def fetch_batch(batch: list[str]) -> tuple[dict[str, dict[str, object]], bool]:
        values = " ".join(f'"{value}"' for value in batch)
        query = f'''SELECT ?imdb ?countryCode ?zhLabel ?zhDescription ?image WHERE {{
            VALUES ?imdb {{ {values} }}
            ?item wdt:P345 ?imdb.
            OPTIONAL {{ ?item wdt:P495 ?country. ?country wdt:P297 ?countryCode. }}
            OPTIONAL {{ ?item rdfs:label ?zhLabel. FILTER(LANG(?zhLabel) = "zh") }}
            OPTIONAL {{ ?item schema:description ?zhDescription. FILTER(LANG(?zhDescription) = "zh") }}
            OPTIONAL {{ ?item wdt:P18 ?image. }}
        }}'''
        request = Request(
            WIKIDATA_ENDPOINT,
            data=urlencode({"query": query, "format": "json"}).encode("utf-8"),
            headers={
                "Accept": "application/sparql-results+json",
                "Content-Type": "application/x-www-form-urlencoded",
                "User-Agent": "lovely-camp-ranking-builder/1.0 (catalog metadata)",
            },
        )
        result: dict[str, dict[str, object]] = {}
        try:
            with urlopen(request, timeout=45) as response:
                payload = json.load(response)
            for binding in ((payload.get("results") or {}).get("bindings") or []):
                imdb_id = str(((binding.get("imdb") or {}).get("value") or "")).strip()
                if not imdb_id:
                    continue
                entry = result.setdefault(imdb_id, {"countries": [], "title": None, "description": None, "poster": None})
                country = str(((binding.get("countryCode") or {}).get("value") or "")).strip()
                if country in REGION_LABELS and country not in entry["countries"]:
                    entry["countries"].append(country)
                title = str(((binding.get("zhLabel") or {}).get("value") or "")).strip()
                if title and has_cjk(title):
                    entry["title"] = title
                description = str(((binding.get("zhDescription") or {}).get("value") or "")).strip()
                if description and has_cjk(description):
                    entry["description"] = description
                image = str(((binding.get("image") or {}).get("value") or "")).strip()
                if image.startswith("http://commons.wikimedia.org/wiki/Special:FilePath/"):
                    entry["poster"] = "https://" + image.removeprefix("http://")
        except Exception:
            # A failed request must stay retryable on the next build.
            return {}, False
        return result, True

    if missing:
        from concurrent.futures import as_completed

        batches = [missing[index:index + WIKIDATA_BATCH_SIZE] for index in range(0, len(missing), WIKIDATA_BATCH_SIZE)]
        # A small pool keeps the public endpoint responsive while making a
        # complete catalog build practical. Failed batches remain uncached and
        # fall back to reviewed metadata/TVmaze country data below.
        with ThreadPoolExecutor(max_workers=3) as pool:
            future_batches = {pool.submit(fetch_batch, batch): batch for batch in batches}
            for future in as_completed(future_batches):
                try:
                    result, success = future.result()
                    if not success:
                        continue
                    cached.update(result)
                    # Negative results are still a successful Wikidata lookup:
                    # remember that the IMDb id has no matching entity so a
                    # later catalog build does not hammer the public endpoint
                    # with the same permanently unresolved batch.
                    for title_id in future_batches[future]:
                        cached.setdefault(
                            title_id,
                            {"countries": [], "title": None, "description": None, "poster": None},
                        )
                except Exception:
                    continue
        try:
            WIKIDATA_COUNTRY_CACHE.parent.mkdir(parents=True, exist_ok=True)
            WIKIDATA_COUNTRY_CACHE.write_text(
                json.dumps(cached, ensure_ascii=False, separators=(",", ":")),
                encoding="utf-8",
            )
        except Exception:
            pass
    return {title_id: cached.get(title_id, {}) for title_id in title_ids}


def wikidata_cache_needs_refresh(entry: dict[str, object] | None) -> bool:
    """Only re-query entries without the required metadata fields.

    Poster is optional: a valid Wikidata identity may have no P18 image.  The
    previous condition treated a missing optional poster as a cache miss and
    repeated tens of thousands of SPARQL requests on every catalog rebuild.
    """
    if not isinstance(entry, dict):
        return True
    return not {"countries", "title", "description"}.issubset(entry.keys())


def cache_poster(url: str | None, canonical_id: str) -> str | None:
    """Cache a bounded release poster so cards remain useful offline."""
    if not isinstance(url, str) or not url.startswith("https://"):
        return url
    filename = re.sub(r"[^a-zA-Z0-9._-]+", "_", canonical_id) + ".img"
    target = POSTER_DIR / filename
    if target.is_file() and target.stat().st_size > 0:
        return f"asset://ranking/posters/{filename}"
    # Public Wikimedia/TVmaze endpoints occasionally reset a parallel request
    # or return a transient gateway response.  One failed attempt used to
    # leave the most visible Chinese-year cards as placeholders even though
    # the same approved URL was reachable moments later.  Keep retries local
    # to the poster cache; metadata generation must remain non-blocking.
    for attempt in range(2):
        try:
            request = Request(url, headers={"User-Agent": "lovely-camp-ranking-builder/1.0"})
            with urlopen(request, timeout=8) as response:
                content_type = (response.headers.get("Content-Type") or "").lower()
                data = response.read(2 * 1024 * 1024 + 1)
            if not content_type.startswith("image/") or len(data) > 2 * 1024 * 1024:
                return url
            target = POSTER_DIR / filename
            target.write_bytes(data)
            return f"asset://ranking/posters/{filename}"
        except Exception:
            if attempt < 1:
                time.sleep(0.15)
    return url


def is_approved_poster_url(url: str | None) -> bool:
    """Return whether a public Wikimedia/TVmaze image may enter the snapshot.

    The generator may see arbitrary upstream URLs.  Keep the image policy
    explicit so a metadata search cannot accidentally turn into an open
    redirect or an unreviewed third-party asset in the Android client.
    """
    if not isinstance(url, str) or not url.startswith("https://"):
        return False
    host = (urlparse(url).hostname or "").lower().rstrip(".")
    return host in {
        "upload.wikimedia.org",
        "thumb.wikimedia.org",
        "commons.wikimedia.org",
        "static.tvmaze.com",
    }


def clean_text(value: str | None, fallback: str = "") -> str:
    value = value or ""
    value = html.unescape(re.sub(r"<[^>]+>", " ", value))
    value = re.sub(r"\s+", " ", value).strip()
    return value[:500] or fallback


def genre_label(value: str) -> str:
    return GENRE_LABELS.get(value, value[:20])


def region_id(value: str | None) -> str | None:
    if not value:
        return None
    match = re.search(r"\b[A-Z]{2}\b", value)
    return match.group(0) if match else None


def imdb_ratings() -> dict[str, tuple[float, int]]:
    ratings_path = TMP / "title.ratings.tsv.gz"
    if not ratings_path.is_file():
        raise SystemExit(f"missing {ratings_path}; download https://datasets.imdbws.com/title.ratings.tsv.gz first")
    result: dict[str, tuple[float, int]] = {}
    with gzip.open(ratings_path, "rt", encoding="utf-8", newline="") as stream:
        rows = csv.DictReader(stream, delimiter="\t")
        for row in rows:
            votes = int(row["numVotes"])
            # Keep the complete rated public slice used by this release.  The
            # 100-vote floor removes one-off noise while retaining substantially
            # more current and regional titles than the previous 1k cutoff.
            # The threshold is part of the published scope; it is not a hidden
            # top-N selection.
            if votes >= MIN_PUBLIC_VOTES:
                result[row["tconst"]] = (float(row["averageRating"]), votes)
    return result


def top_imdb_movies(ratings: dict[str, tuple[float, int]], limit: int = 28) -> list[dict]:
    basics_path = TMP / "title.basics.tsv.gz"
    if not basics_path.is_file():
        raise SystemExit(f"missing {basics_path}; download https://datasets.imdbws.com/title.basics.tsv.gz first")
    candidates: list[tuple[float, dict]] = []
    with gzip.open(basics_path, "rt", encoding="utf-8", newline="") as stream:
        rows = csv.DictReader(stream, delimiter="\t")
        for row in rows:
            if row["titleType"] != "movie" or row["startYear"] == r"\N":
                continue
            try:
                year = int(row["startYear"])
            except ValueError:
                continue
            if year < 1950 or year > 2026 or row["tconst"] not in ratings:
                continue
            score, votes = ratings[row["tconst"]]
            # Votes provide confidence toward a neutral prior; they do not
            # multiply the rating and therefore cannot create an out-of-scale
            # score for titles with large fan communities.
            weighted = ranking_bayesian_score(score, votes)
            candidates.append((weighted, {**row, "score": score, "votes": votes, "year": year}))
    candidates.sort(key=lambda item: (-item[0], -item[1]["score"], -item[1]["votes"], item[1]["tconst"]))
    candidate_by_id = {row["tconst"]: row for _, row in candidates}
    # A single all-time score list hides recent titles behind classics. Keep a
    # recent tranche first, then fill the remaining slots with the weighted
    # all-time list so the UI has both eras.
    recent = [row for _, row in candidates if row["year"] >= 2016]
    selected = recent[: min(24, limit)]
    selected_ids = {row["tconst"] for row in selected}
    selected.extend(row for _, row in candidates if row["tconst"] not in selected_ids)
    selected = selected[:limit]

    # Reserve slots for regional cinema after the recent tranche is applied.
    # The replacement is deterministic: the lowest-ranked non-seed entry is
    # evicted first, so the list remains score-ordered after the final sort in
    # build_items while every requested region has real source-backed rows.
    regional_ids = [
        tconst
        for region in ("CN", "KR", "TH")
        for tconst in REGIONAL_MOVIE_SEEDS[region]
        if tconst in candidate_by_id
    ]
    for tconst in regional_ids:
        if tconst in {row["tconst"] for row in selected}:
            continue
        replace_index = next(
            (index for index in range(len(selected) - 1, -1, -1) if selected[index]["tconst"] not in regional_ids),
            None,
        )
        if replace_index is None:
            break
        selected[replace_index] = candidate_by_id[tconst]
    return selected[:limit]


def wikipedia_localized_title(title: str) -> str | None:
    """Resolve a Chinese Wikipedia title without scraping protected pages."""
    override = LOCALIZED_TITLE_OVERRIDES.get(title)
    if override:
        return override
    if has_cjk(title):
        return title
    try:
        query = urlencode(
            {"action": "query", "list": "search", "srsearch": title, "srlimit": 5, "format": "json", "utf8": 1}
        )
        payload = fetch_json(f"https://zh.wikipedia.org/w/api.php?{query}", timeout=8)
        results = ((payload.get("query") or {}).get("search") or [])
        candidates = [str(item.get("title")) for item in results if isinstance(item, dict)]
        return choose_localized_title(title, candidates) if candidates else None
    except Exception:
        return None


def wikipedia_summary(title: str, language: str = "en") -> tuple[str, str | None]:
    # REST summary is a public metadata endpoint; failures use a deterministic
    # text fallback and do not remove the title from the feed.
    try:
        url = f"https://{language}.wikipedia.org/api/rest_v1/page/summary/" + quote(title.replace(" ", "_"))
        payload = fetch_json(url, timeout=8)
        summary = clean_text(payload.get("extract"), f"《{title}》的公开简介暂缺。")
        thumb = (payload.get("thumbnail") or {}).get("source")
        if isinstance(thumb, str) and is_approved_poster_url(thumb):
            return summary, thumb
    except Exception:
        pass
    return f"《{title}》的公开简介暂缺。", None


def wikipedia_search_summary(title: str, language: str = "zh") -> tuple[str, str | None]:
    """Resolve a localized page when the source title is a subtitle/variant.

    Recent Chinese productions frequently use an IMDb romanisation or a
    punctuation variant while the Chinese Wikipedia page uses a shorter
    canonical title (for example ``凸变英雄X`` → ``凸变英雄``).  An exact REST
    lookup therefore misses a real poster.  Search is restricted to the
    localized Wikipedia API and accepts only a CJK candidate that overlaps
    the requested title; it never guesses from an unrelated English result.
    """
    direct_summary, direct_poster = wikipedia_summary(title, language=language)
    if direct_poster or not has_cjk(title):
        return direct_summary, direct_poster
    try:
        query = urlencode({
            "action": "query",
            "list": "search",
            "srsearch": title,
            "srlimit": 5,
            "format": "json",
            "utf8": 1,
        })
        payload = fetch_json(f"https://{language}.wikipedia.org/w/api.php?{query}", timeout=8)
        candidates = [
            clean_text(item.get("title"))
            for item in ((payload.get("query") or {}).get("search") or [])
            if isinstance(item, dict)
        ]
        compact_title = re.sub(r"[^\u3400-\u9fffA-Za-z0-9]+", "", title).lower()
        for candidate in candidates:
            if not candidate or not has_cjk(candidate):
                continue
            compact_candidate = re.sub(r"[^\u3400-\u9fffA-Za-z0-9]+", "", candidate).lower()
            if not compact_candidate or not (compact_candidate in compact_title or compact_title in compact_candidate):
                continue
            summary, poster = wikipedia_summary(candidate, language=language)
            if poster or "公开简介暂缺" not in summary:
                return summary, poster
    except Exception:
        pass
    return direct_summary, direct_poster


def regional_tvmaze_queries(items: Iterable[dict], current_year: int | None = None) -> list[str]:
    """Return bounded recent Chinese titles for targeted TVmaze enrichment.

    TVmaze's paged index is ordered by numeric id rather than freshness, so a
    page-only crawl misses new Chinese shows (for example ``逐玉`` and
    ``凸变英雄X``).  Search the public index by the already verified Chinese
    catalogue title.  Only CN rows from the current and previous two years are
    targeted; Korean/Thai discovery remains on the reviewed seed list, keeping
    the build bounded and deterministic.
    """
    year = current_year or datetime.now(timezone.utc).year
    seen: set[str] = set()
    queries: list[str] = []
    for item in items:
        if not isinstance(item, dict) or "CN" not in item.get("countries", []):
            continue
        release_year = item.get("releaseYear")
        title = clean_text(str(item.get("displayTitle") or ""))
        if not isinstance(release_year, int) or release_year < year - 2 or not has_cjk(title) or title in seen:
            continue
        seen.add(title)
        queries.append(title)
    return queries


def tvmaze_page_numbers(page_count: int = TVMAZE_PAGE_COUNT) -> range:
    """Return the complete public TVmaze page scope for a snapshot build."""
    if page_count < 1:
        raise ValueError("page_count must be positive")
    return range(page_count)


def _read_tvmaze_index_cache() -> list[dict] | None:
    try:
        if not TVMAZE_INDEX_CACHE.is_file():
            return None
        age = time.time() - TVMAZE_INDEX_CACHE.stat().st_mtime
        if age < 0 or age > TVMAZE_CACHE_MAX_AGE_SECONDS:
            return None
        payload = json.loads(TVMAZE_INDEX_CACHE.read_text(encoding="utf-8"))
        shows = payload.get("shows") if isinstance(payload, dict) else None
        if not isinstance(shows, list) or len(shows) < 10_000:
            return None
        return [show for show in shows if isinstance(show, dict)]
    except Exception:
        return None


def _write_tvmaze_index_cache(shows: list[dict]) -> None:
    try:
        TVMAZE_INDEX_CACHE.parent.mkdir(parents=True, exist_ok=True)
        TVMAZE_INDEX_CACHE.write_text(
            json.dumps({"fetchedAt": datetime.now(timezone.utc).isoformat(), "shows": shows}, ensure_ascii=False),
            encoding="utf-8",
        )
    except Exception:
        pass


def tvmaze_shows(extra_queries: Iterable[str] = ()) -> list[dict]:
    shows: list[dict] = _read_tvmaze_index_cache() or []
    # The public index is ordered by numeric ID, not by release date.  A full
    # page scan is required for a complete TVmaze-backed series/variety slice;
    # title searches below still supplement the index for newly added regional
    # rows that have not reached a stable page yet.
    def fetch_page(page: int):
        try:
            return fetch_json(f"https://api.tvmaze.com/shows?page={page}", timeout=12)
        except Exception:
            return None

    if not shows:
        page_results: list[dict] = []
        with ThreadPoolExecutor(max_workers=12) as pool:
            for data in pool.map(fetch_page, tvmaze_page_numbers()):
                if isinstance(data, list):
                    page_results.extend(item for item in data if isinstance(item, dict))
        # Do not persist a partial index after a transient outage.  A full
        # catalogue rebuild can still use the successful pages in memory, but
        # the next run must retry rather than treating the partial result as a
        # complete source snapshot.
        if len(page_results) >= 80_000:
            _write_tvmaze_index_cache(page_results)
        shows.extend(page_results)

    def fetch_seed(query: str):
        try:
            return fetch_json(f"https://api.tvmaze.com/search/shows?q={quote(query)}", timeout=12)
        except Exception:
            return None

    seed_queries = list(dict.fromkeys([*REGIONAL_SEED_QUERIES, *extra_queries]))
    with ThreadPoolExecutor(max_workers=8) as pool:
        for data in pool.map(fetch_seed, seed_queries):
            if isinstance(data, list) and data and isinstance(data[0], dict):
                show = data[0].get("show")
                if isinstance(show, dict):
                    shows.append(show)
    deduped: dict[int, dict] = {}
    for show in shows:
        show_id = show.get("id")
        if isinstance(show_id, int):
            deduped[show_id] = show
    return list(deduped.values())


def tvmaze_aka_names(show: dict) -> list[str]:
    show_id = show.get("id")
    if not isinstance(show_id, int):
        return []
    try:
        data = fetch_json(f"https://api.tvmaze.com/shows/{show_id}/akas", timeout=8)
    except Exception:
        return []
    if not isinstance(data, list):
        return []
    return [clean_text(item.get("name")) for item in data if isinstance(item, dict) and item.get("name")]


def rank_entry(
    *,
    canonical_id: str,
    title: str,
    original_title: str | None,
    aliases: list[str],
    media_type: str,
    year: int | None,
    countries: list[str],
    genres: list[str],
    poster_url: str | None,
    summary: str,
    score: float | None,
    ratings: list[dict],
    source_ids: list[str],
    localized_title: str | None = None,
    release_markets: list[str] | None = None,
    country_source: str = "unknown",
    coverage_complete: bool = True,
) -> dict:
    display_title = localized_title or title
    normalized_aliases: list[str] = []
    for alias in [*aliases, title]:
        cleaned = clean_text(alias)
        if cleaned and cleaned != display_title and cleaned not in normalized_aliases:
            normalized_aliases.append(cleaned)
    return {
        "canonicalId": canonical_id,
        "rank": None,
        "displayTitle": display_title,
        "originalTitle": original_title or (title if display_title != title else None),
        "aliases": normalized_aliases,
        "mediaType": media_type,
        "releaseYear": year,
        "countries": countries,
        "releaseMarkets": sorted(set(release_markets or [])),
        "countrySource": country_source,
        "genres": genres,
        "posterUrl": poster_url,
        "summary": chinese_summary_or_fallback(
            summary,
            display_title,
            year=year,
            countries=countries,
            genres=genres,
        ),
        "aggregateScore": None if score is None else round(max(0.0, min(10.0, score)), 1),
        "sourceRatings": ratings,
        "sourceRanks": [],
        "coverage": {"sourceCount": len(source_ids), "coveredSourceIds": source_ids, "complete": coverage_complete},
        "searchTitle": display_title,
    }


def build_items_legacy(shows: list[dict] | None = None) -> list[dict]:
    ratings = imdb_ratings()
    items: list[dict] = []
    shows = shows if shows is not None else tvmaze_shows()
    tv_candidates: list[tuple[float, dict]] = []
    for show in shows:
        name = clean_text(show.get("name"), "未命名剧集")
        genres_raw = [str(value) for value in (show.get("genres") or [])]
        media_type = "VARIETY" if VARIETY_GENRES.intersection(genres_raw) or name in VARIETY_TITLE_HINTS else "SERIES"
        rating = float(((show.get("rating") or {}).get("average") or 0.0))
        weight = float(show.get("weight") or 0.0)
        imdb_id = (show.get("externals") or {}).get("imdb")
        imdb = ratings.get(imdb_id) if imdb_id else None
        score = (rating * 0.4 + imdb[0] * 0.6) if imdb else (rating if rating > 0 else None)
        weighted_score = score if score is not None else 0.0
        tv_candidates.append((weighted_score + math.log10(max(1.0, weight)) * 0.05, {"show": show, "rating": rating, "imdb": imdb, "score": score, "media": media_type}))

    selected_tv_items = [
        selected_item
        for media_type in ("SERIES", "VARIETY")
        for selected_item in select_stratified_tv_candidates(tv_candidates, media_type, limit=64)
    ]
    aka_by_id: dict[int, list[str]] = {}
    with ThreadPoolExecutor(max_workers=8) as pool:
        futures = {
            pool.submit(tvmaze_aka_names, selected_item["show"]): selected_item["show"].get("id")
            for selected_item in selected_tv_items
        }
        for future, show_id in futures.items():
            if isinstance(show_id, int):
                try:
                    aka_by_id[show_id] = future.result()
                except Exception:
                    aka_by_id[show_id] = []

    def fetch_tv_summary(selected_item: dict) -> tuple[int | None, str | None]:
        show = selected_item["show"]
        show_id = show.get("id")
        name = clean_text(show.get("name"), "未命名剧集")
        aka_names = aka_by_id.get(show_id, [])
        imdb_id = (show.get("externals") or {}).get("imdb")
        localized_title = str(
            (IMDB_METADATA_OVERRIDES.get(imdb_id, {}) or {}).get("title")
            or preferred_localized_title(name, aka_names)
        )
        summary_title = localized_title if has_cjk(localized_title) else name
        try:
            summary, _ = wikipedia_summary(summary_title, language="zh")
            return show_id, summary
        except Exception:
            return show_id, None

    summary_by_id: dict[int, str] = {}
    with ThreadPoolExecutor(max_workers=6) as pool:
        summary_futures = [pool.submit(fetch_tv_summary, selected_item) for selected_item in selected_tv_items]
        for future in summary_futures:
            try:
                show_id, summary = future.result()
            except Exception:
                continue
            if isinstance(show_id, int) and summary:
                summary_by_id[show_id] = summary

    for selected_item in selected_tv_items:
        show = selected_item["show"]
        name = clean_text(show.get("name"), "未命名剧集")
        media_type = selected_item["media"]
        country_codes = show_country_codes(show)
        year = show_year(show)
        genres = [genre_label(str(value)) for value in (show.get("genres") or [])][:4]
        aka_names = aka_by_id.get(show.get("id"), [])
        imdb_id = (show.get("externals") or {}).get("imdb")
        localized_title = str(
            (IMDB_METADATA_OVERRIDES.get(imdb_id, {}) or {}).get("title")
            or preferred_localized_title(name, aka_names)
        )
        show_rating = selected_item["rating"]
        # Keep recent regional titles even when a public source has not
        # published a score yet.  They are shown without a fabricated zero and
        # remain paginatable/searchable; older unrated rows stay out of the
        # ranking so score order remains meaningful.
        has_recent_regional_identity = (
            not selected_item["imdb"]
            and show_rating <= 0
            and year is not None
            and year >= datetime.now(timezone.utc).year - 2
            and bool(set(country_codes).intersection({"CN", "KR", "TH"}))
        )
        if not selected_item["imdb"] and show_rating <= 0 and not has_recent_regional_identity:
            continue
        source_ratings = [{"sourceId": "tvmaze", "value": round(show_rating, 1) if show_rating > 0 else None, "scaleMax": 10.0, "voteCount": None}]
        source_ids = ["tvmaze"]
        if selected_item["imdb"]:
            score, votes = selected_item["imdb"]
            source_ratings.append({"sourceId": "imdb", "value": round(score, 1), "scaleMax": 10.0, "voteCount": votes})
            source_ids.append("imdb")
        items.append(rank_entry(
            canonical_id=f"imdb:{(show.get('externals') or {}).get('imdb')}" if (show.get('externals') or {}).get('imdb') else f"tvmaze:{show.get('id')}",
            title=name,
            original_title=None,
            media_type=media_type,
            year=year,
            countries=country_codes,
            genres=genres,
            poster_url=((show.get("image") or {}).get("medium") or (show.get("image") or {}).get("original")),
            summary=summary_by_id.get(show.get("id"), clean_text(show.get("summary"), f"{name} 的节目简介暂缺。")),
            score=selected_item["score"],
            ratings=source_ratings,
            source_ids=source_ids,
            aliases=aka_names,
            localized_title=localized_title,
            country_source="tvmaze-network" if country_codes else "unresolved",
            coverage_complete=bool(selected_item["imdb"] or show_rating > 0),
        ))

    movie_rows = top_imdb_movies(ratings, limit=60)

    def fetch_movie_metadata(movie: dict) -> tuple[dict, str | None, str, str | None]:
        title = clean_text(movie.get("primaryTitle"), "未命名电影")
        localized_title = REGIONAL_MOVIE_TITLES.get(movie.get("tconst")) or wikipedia_localized_title(title)
        summary_title = localized_title or title
        summary, poster = wikipedia_summary(summary_title, language="zh" if localized_title else "en")
        return movie, localized_title, summary, poster

    movie_metadata: list[tuple[dict, str | None, str, str | None]] = []
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = [pool.submit(fetch_movie_metadata, movie) for movie in movie_rows]
        for index, future in enumerate(futures):
            try:
                movie_metadata.append(future.result())
            except Exception:
                movie = movie_rows[index]
                title = clean_text(movie.get("primaryTitle"), "未命名电影")
                movie_metadata.append((movie, None, f"《{title}》的公开简介暂缺。", None))

    for movie, localized_title, summary, poster in movie_metadata:
        title = clean_text(movie.get("primaryTitle"), "未命名电影")
        score = float(movie["score"])
        country_codes = [region for region, ids in REGIONAL_MOVIE_SEEDS.items() if movie["tconst"] in ids]
        if not country_codes:
            country_codes = MOVIE_COUNTRY_BY_TITLE.get(title)
        if not country_codes:
            original = clean_text(movie.get("originalTitle"))
            country_codes = MOVIE_COUNTRY_BY_TITLE.get(original, [])
        items.append(rank_entry(
            canonical_id=f"imdb:{movie['tconst']}",
            title=title,
            original_title=None if movie.get("originalTitle") in (None, r"\N", title) else movie.get("originalTitle"),
            aliases=[],
            media_type="MOVIE",
            year=movie["year"],
            countries=country_codes,
            genres=[genre_label(value) for value in str(movie.get("genres", "")).split(",") if value and value != r"\N"][:4],
            poster_url=poster,
            summary=summary,
            score=score,
            ratings=[{"sourceId": "imdb", "value": round(score, 1), "scaleMax": 10.0, "voteCount": movie["votes"]}],
            source_ids=["imdb"],
            localized_title=localized_title,
        ))

    # Cache posters after canonical IDs are stable; the source URL remains in
    # the generator inputs while the app receives only a safe asset URI.
    POSTER_DIR.mkdir(parents=True, exist_ok=True)
    def cache_item_poster(item: dict) -> tuple[str, str | None]:
        return item["canonicalId"], cache_poster(item.get("posterUrl"), item["canonicalId"])

    with ThreadPoolExecutor(max_workers=8) as pool:
        poster_results = list(pool.map(cache_item_poster, items))
    poster_by_id = dict(poster_results)
    for item in items:
        item["posterUrl"] = poster_by_id[item["canonicalId"]]

    # Stable order makes the generated asset easy to review and hash.
    return sorted(items, key=lambda item: (item["mediaType"], -(item["aggregateScore"] or 0.0), item["canonicalId"]))


IMDB_TITLE_TYPES = {
    "movie": "MOVIE",
    "tvMovie": "MOVIE",
    "tvSeries": "SERIES",
    "tvMiniSeries": "SERIES",
    "tvSpecial": "VARIETY",
}
IMDB_VARIETY_GENRES = {
    "Reality-TV", "Game-Show", "Talk-Show", "News", "Music", "Sport",
}


def imdb_title_rows(ratings: dict[str, tuple[float, int]]) -> list[dict]:
    """Load every rated film/show in the release scope, not a top-N slice."""
    basics_path = TMP / "title.basics.tsv.gz"
    if not basics_path.is_file():
        raise SystemExit(f"missing {basics_path}; download https://datasets.imdbws.com/title.basics.tsv.gz first")
    rows: list[dict] = []
    with gzip.open(basics_path, "rt", encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        for row in reader:
            media_type = IMDB_TITLE_TYPES.get(row.get("titleType"))
            if media_type is None or row.get("tconst") not in ratings:
                continue
            start_year = row.get("startYear") or ""
            if not start_year.isdigit():
                continue
            year = int(start_year)
            if year < 1950 or year > datetime.now(timezone.utc).year:
                continue
            genres = [value for value in str(row.get("genres") or "").split(",") if value and value != r"\N"]
            if "Adult" in genres:
                continue
            if media_type == "SERIES" and IMDB_VARIETY_GENRES.intersection(genres):
                media_type = "VARIETY"
            score, votes = ratings[row["tconst"]]
            rows.append({**row, "media": media_type, "year": year, "score": score, "votes": votes, "genresRaw": genres})
    return rows


def imdb_alias_index(title_ids: set[str]) -> dict[str, dict[str, object]]:
    """Read regional names/country evidence from IMDb's public AKA dataset."""
    akas_path = TMP / "title.akas.tsv.gz"
    if not akas_path.is_file():
        raise SystemExit(f"missing {akas_path}; download https://datasets.imdbws.com/title.akas.tsv.gz first")
    result: dict[str, dict[str, object]] = {}
    with gzip.open(akas_path, "rt", encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        for row in reader:
            title_id = row.get("titleId")
            if title_id not in title_ids:
                continue
            alias = clean_text(row.get("title"))
            if not alias or alias == r"\N":
                continue
            region = clean_text(row.get("region"))
            language = clean_text(row.get("language"))
            entry = result.setdefault(title_id, {"regions": set(), "aliases": [], "zh": []})
            regions = entry["regions"]
            aliases = entry["aliases"]
            zh_aliases = entry["zh"]
            if region in REGION_LABELS:
                regions.add(region)
            if alias not in aliases and len(aliases) < 12:
                aliases.append(alias)
            if (
                region in {"CN", "TW", "HK"}
                or language in {"cmn", "zho", "yue"}
                or has_cjk(alias)
            ) and alias not in zh_aliases and len(zh_aliases) < 8:
                zh_aliases.append(alias)
    return result


def build_full_imdb_items(ratings: dict[str, tuple[float, int]]) -> list[dict]:
    """Build a full, paginatable IMDb-backed catalogue for the supported scope."""
    rows = imdb_title_rows(ratings)
    aliases_by_id = imdb_alias_index({row["tconst"] for row in rows})
    all_title_ids = {row["tconst"] for row in rows}
    # The catalogue is paginated across all years, so metadata enrichment must
    # not silently stop at the recent regional slice.  The cache keeps this
    # repeatable: only IDs without the required identity fields are queried on
    # later builds.
    wikidata_by_id = wikidata_index(all_title_ids, query_missing_ids=all_title_ids)
    items: list[dict] = []
    for row in rows:
        title_id = row["tconst"]
        primary = clean_text(row.get("primaryTitle"), "未命名作品")
        original = clean_text(row.get("originalTitle"), primary)
        alias_data = aliases_by_id.get(title_id, {"regions": set(), "aliases": [], "zh": []})
        wikidata = wikidata_by_id.get(title_id, {})
        # Keep the user-facing search bridge useful without copying the full
        # IMDb AKA fan-out (one title can have dozens of market spellings).
        # The primary/original title is retained by rank_entry; only a small
        # localized alias set is bundled for Chinese-first search.
        aliases = list(alias_data["zh"][:4])
        metadata_override = IMDB_METADATA_OVERRIDES.get(title_id, {})
        wikidata_title = str(wikidata.get("title") or "")
        localized_raw = str(
            metadata_override.get("title")
            or (wikidata_title if is_chinese_title_candidate(wikidata_title) else "")
            or preferred_localized_title(primary, alias_data["zh"])
        )
        localized = extract_chinese_title(localized_raw) or localized_raw
        if localized == primary and not is_chinese_title_candidate(primary):
            override = LOCALIZED_TITLE_OVERRIDES.get(primary)
            localized = override or primary
        # ``title.akas.region`` is a release/alias market, never production
        # country.  Use Wikidata's country-of-origin claim first, then only
        # reviewed title fallbacks.  Keeping the AKA markets separately lets
        # us retain that provenance without corrupting the country filter.
        countries = sorted(str(code) for code in (wikidata.get("countries") or []) if code in REGION_LABELS)
        country_source = "wikidata-p495" if countries else "unresolved"
        if not countries:
            countries = list(MOVIE_COUNTRY_BY_TITLE.get(primary) or MOVIE_COUNTRY_BY_TITLE.get(original) or [])
            country_source = "reviewed-title-map" if countries else country_source
        if not countries:
            description = str(wikidata.get("description") or "")
            if any(marker in description for marker in ("中国", "中國", "大陆", "大陸")):
                countries = ["CN"]
                country_source = "wikidata-description"
        if not countries:
            countries = list(RECENT_PRODUCTION_COUNTRY_OVERRIDES.get(primary) or RECENT_PRODUCTION_COUNTRY_OVERRIDES.get(original) or [])
            country_source = "reviewed-recent-title-map" if countries else country_source
        release_markets = sorted(str(code) for code in alias_data["regions"] if code in REGION_LABELS)
        genres = [genre_label(value) for value in row["genresRaw"]][:4]
        title_for_summary = localized if is_chinese_title_candidate(localized) else primary
        wikidata_description = str(wikidata.get("description") or "")
        items.append(rank_entry(
            canonical_id=f"imdb:{title_id}",
            title=primary,
            original_title=None if original == primary else original,
            aliases=aliases,
            media_type=row["media"],
            year=row["year"],
            countries=countries,
            genres=genres,
            poster_url=metadata_override.get("poster") or wikidata.get("poster"),
            summary=chinese_summary_or_fallback(
                metadata_override.get("summary") or wikidata_description,
                title_for_summary,
                year=row["year"],
                countries=countries,
                genres=genres,
            ),
            score=float(row["score"]),
            ratings=[{
                "sourceId": "imdb",
                "value": round(float(row["score"]), 1),
                "scaleMax": 10.0,
                "voteCount": row["votes"],
            }],
            source_ids=["imdb"],
            localized_title=localized,
            release_markets=release_markets,
            country_source=country_source,
        ))
    return items


def localize_prominent_items(items: list[dict], limit: int = 600) -> None:
    """Resolve Chinese names and public metadata for visible catalogue rows.

    IMDb's AKA file is the authoritative bulk identity source, but some new
    or niche titles have no Chinese AKA.  A bounded lookup keeps the default
    first screen Chinese-first without pretending that every long-tail title
    has a verified translation.
    """
    candidates = localization_candidates(items, limit=limit)

    def resolve(item: dict) -> tuple[dict, str | None, str | None, str | None]:
        primary = item.get("originalTitle") or item["displayTitle"]
        # The bulk IMDb/Wikidata pass cannot cover every title.  For the
        # prominent rows that are sent through this bounded enrichment pass,
        # resolve a verified Chinese Wikipedia title as well as the summary
        # and poster.  The resolver is conservative: it only returns a CJK
        # page title and otherwise leaves the source title unchanged.
        localized = None
        if not is_chinese_title_candidate(str(item.get("displayTitle") or "")):
            localized = wikipedia_localized_title(str(primary))
            if not localized or not is_chinese_title_candidate(localized):
                localized = preferred_localized_title(str(primary), item.get("aliases") or [])
        summary_title = str(localized or item["displayTitle"] if is_chinese_title_candidate(str(localized or item.get("displayTitle") or "")) else primary)
        language = "zh" if has_cjk(summary_title) else "en"
        summary, poster = wikipedia_search_summary(summary_title, language=language)
        return item, localized, summary, poster

    with ThreadPoolExecutor(max_workers=8) as pool:
        for item, localized, summary, poster in pool.map(resolve, candidates):
            if localized and is_chinese_title_candidate(localized) and localized != item["displayTitle"]:
                primary = item["displayTitle"]
                item["displayTitle"] = localized
                item["searchTitle"] = localized
                item["originalTitle"] = item.get("originalTitle") or primary
                aliases = list(item.get("aliases") or [])
                if primary not in aliases:
                    aliases.append(primary)
                item["aliases"] = aliases[:6]
            if poster and is_approved_poster_url(poster):
                item["posterUrl"] = poster
            if summary and has_cjk(summary) and "公开简介暂缺" not in summary and "公开剧情简介暂缺" not in summary:
                item["summary"] = chinese_summary_or_fallback(
                    summary,
                    item["displayTitle"],
                    year=item.get("releaseYear"),
                    countries=item.get("countries") or [],
                    genres=item.get("genres") or [],
                )


def localization_candidates(items: list[dict], limit: int = 600) -> list[dict]:
    """Choose enrichment rows without starving the China board.

    The complete IMDb slice is intentionally larger than a bounded public
    metadata lookup.  Prioritising only recent CN/KR/TH rows still lets the
    global score tail consume the budget before older Chinese films and
    documentaries can receive a public poster or synopsis.  China is the
    primary user-facing board, so all eligible CN rows are considered before
    the requested Asian regions and finally the global tail.  This changes
    only enrichment priority; it never removes or re-ranks catalogue rows.
    """
    ranked = sorted(
        items,
        key=lambda item: (-float(item.get("aggregateScore") or 0.0), item.get("canonicalId", "")),
    )
    current_year = datetime.now(timezone.utc).year
    priority_groups = [
        [item for item in ranked if "CN" in item.get("countries", [])],
        [
            item for item in ranked
            if item.get("releaseYear", 0) >= current_year - 2
            and any(code in {"KR", "TH"} for code in item.get("countries", []))
        ],
        ranked,
    ]
    candidates: list[dict] = []
    seen: set[str] = set()
    for group in priority_groups:
        for item in group:
            canonical_id = item.get("canonicalId", "")
            if canonical_id in seen:
                continue
            if not item.get("posterUrl") or not is_chinese_title_candidate(item["displayTitle"]) or "当前快照未收录剧情正文" in str(item.get("summary") or ""):
                candidates.append(item)
                seen.add(canonical_id)
            if len(candidates) >= limit:
                return candidates
    return candidates


def enrich_imdb_items_with_tvmaze(items: list[dict], shows: list[dict]) -> None:
    """Attach TVmaze ratings and safe metadata to every matching IMDb row.

    The old generator only retained the 128 TVmaze rows selected for the
    legacy enrichment cards.  That made the apparent two-source catalogue
    overwhelmingly IMDb-only.  TVmaze's public index already exposes IMDb
    external IDs for thousands of shows; merge those ratings into the complete
    IMDb slice without reducing the slice to a TVmaze top-N.
    """
    by_imdb: dict[str, dict] = {}
    for show in shows:
        externals = show.get("externals") or {}
        imdb_id = externals.get("imdb")
        rating_value = ((show.get("rating") or {}).get("average"))
        if not isinstance(imdb_id, str) or not imdb_id.startswith("tt"):
            continue
        try:
            rating = float(rating_value or 0.0)
        except (TypeError, ValueError):
            continue
        if rating <= 0:
            continue
        by_imdb[imdb_id] = {"show": show, "rating": rating}

    for item in items:
        canonical = str(item.get("canonicalId") or "")
        imdb_id = canonical.removeprefix("imdb:")
        match = by_imdb.get(imdb_id)
        if not match:
            continue
        show = match["show"]
        tvmaze_rating = match["rating"]
        imdb_rating = float(item.get("aggregateScore") or 0.0)
        item["aggregateScore"] = round((imdb_rating + tvmaze_rating) / 2.0, 1)
        source_ratings = list(item.get("sourceRatings") or [])
        if not any(entry.get("sourceId") == "tvmaze" for entry in source_ratings):
            source_ratings.append({
                "sourceId": "tvmaze",
                "value": round(tvmaze_rating, 1),
                "scaleMax": 10.0,
                "voteCount": None,
            })
        item["sourceRatings"] = source_ratings
        coverage_ids = list(item.get("coverage", {}).get("coveredSourceIds") or [])
        if "tvmaze" not in coverage_ids:
            coverage_ids.append("tvmaze")
        item["coverage"] = {
            "sourceCount": len(coverage_ids),
            "coveredSourceIds": coverage_ids,
            "complete": True,
        }
        if not item.get("posterUrl"):
            image = show.get("image") or {}
            item["posterUrl"] = image.get("original") or image.get("medium")
        if not item.get("countries"):
            item["countries"] = show_country_codes(show)
        if not is_chinese_title_candidate(str(item.get("displayTitle") or "")):
            name = clean_text(show.get("name"), "")
            localized = str(
                (IMDB_METADATA_OVERRIDES.get(imdb_id, {}) or {}).get("title")
                or preferred_localized_title(name, [])
            )
            if is_chinese_title_candidate(localized):
                old_title = item["displayTitle"]
                item["displayTitle"] = localized
                item["searchTitle"] = localized
                aliases = list(item.get("aliases") or [])
                if old_title not in aliases:
                    aliases.append(old_title)
                item["aliases"] = aliases[:6]


def build_items() -> list[dict]:
    """Merge enriched TVmaze cards with the complete IMDb rated slice."""
    ratings = imdb_ratings()
    full_items = build_full_imdb_items(ratings)
    # Enrich the complete China slice before spending the remaining bounded
    # lookup budget on Korean/Thai and global long-tail rows.  This keeps the
    # user's primary country filter from rendering a sea of placeholder cards.
    localize_prominent_items(full_items, limit=3000)
    shows = tvmaze_shows(regional_tvmaze_queries(full_items))
    # Keep the existing TVmaze/Wikipedia enrichment for the highest-visibility
    # cards (posters and detailed Chinese summaries), while the IMDb rows make
    # every supported year/country query genuinely paginatable.
    enriched_items = build_items_legacy(shows)
    # Merge ratings for every IMDb-linked TVmaze show, not only the 128 legacy
    # cards selected for rich summaries.
    enrich_imdb_items_with_tvmaze(full_items, shows)
    by_id = {item["canonicalId"]: item for item in full_items}
    for item in enriched_items:
        by_id[item["canonicalId"]] = item
    # Cache all approved poster URLs after the full merge.  This gives the
    # most visible catalogue rows real offline art while leaving long-tail
    # URLs available for the safe remote loader when a release chooses it.
    POSTER_DIR.mkdir(parents=True, exist_ok=True)
    def cache_item_poster(item: dict) -> tuple[str, str | None]:
        return item["canonicalId"], cache_poster(item.get("posterUrl"), item["canonicalId"])

    # Do not inflate the APK with tens of thousands of copies.  Cache rows
    # that are visible on the default board plus every row in the recent
    # CN/KR/TH year slices users explicitly asked to browse.  Other approved
    # URLs remain remote metadata and use the allowlisted loader.  This keeps
    # pagination honest: later pages are real cards, not placeholders merely
    # because they fell outside a visual budget.
    ranked_for_cache = sorted(
        by_id.values(),
        key=lambda item: (-float(item.get("aggregateScore") or 0.0), item.get("canonicalId", "")),
    )
    cache_ids = {item["canonicalId"] for item in ranked_for_cache[:40]}
    current_year = datetime.now(timezone.utc).year
    for region in ("CN", "KR", "TH"):
        for year in range(current_year, current_year - 3, -1):
            recent_regional = [
                item for item in ranked_for_cache
                if item.get("releaseYear") == year and region in item.get("countries", [])
            ]
            # The first 30 rows are the device-visible pages.  Long-tail rows
            # remain in the full data set with an honest placeholder instead
            # of making catalog generation wait on hundreds of image hosts.
            cache_ids.update(item["canonicalId"] for item in recent_regional[:30])
    cache_candidates = [item for item in by_id.values() if item.get("posterUrl") and item["canonicalId"] in cache_ids]
    with ThreadPoolExecutor(max_workers=8) as pool:
        poster_results = list(pool.map(cache_item_poster, cache_candidates))
    poster_by_id = dict(poster_results)
    for item in by_id.values():
        if item["canonicalId"] in poster_by_id:
            item["posterUrl"] = poster_by_id[item["canonicalId"]]
    return sorted(by_id.values(), key=lambda item: (item["mediaType"], -(item["aggregateScore"] or 0.0), item["canonicalId"]))


def build_catalog(items: list[dict]) -> dict:
    source_ids = ["tvmaze", "imdb"]
    regions = sorted({region for item in items for region in item["countries"] if region in REGION_LABELS})
    genres = sorted({genre for item in items for genre in item["genres"] if genre})
    years = sorted({item["releaseYear"] for item in items if item["releaseYear"]}, reverse=True)
    return {
        "catalogVersion": CATALOG_VERSION,
        "generatedAt": DATA_AS_OF,
        "methodologyVersion": METHODOLOGY_VERSION,
        "identityMapVersion": IDENTITY_MAP_VERSION,
        "debugFixture": False,
        "testDataOnly": False,
        "sourceStatuses": [
            {"sourceId": "tvmaze", "status": "AVAILABLE"},
            {"sourceId": "imdb", "status": "AVAILABLE"},
        ],
        "coverage": {"sourceCount": len(source_ids), "coveredSourceIds": source_ids, "complete": True},
        "metadataCoverage": metadata_coverage(items),
        "capabilities": {
            "schemaVersion": 1,
            "boards": [
                {"id": "aggregate", "label": "综合评分"},
                {"id": "source-imdb", "label": "IMDb评分"},
                {"id": "source-tvmaze", "label": "剧集评分"},
            ],
            "mediaTypes": ["ALL", "MOVIE", "SERIES", "VARIETY"],
            # The Compose row already owns the explicit "全部" chip; only
            # declare non-empty period filters here to avoid a duplicate UI chip.
            "periods": [{"id": "year", "label": "年度"}],
            "regions": [
                {"id": value, "label": REGION_LABELS[value]}
                for value in [
                    "CN", "KR", "TH", "TW", "HK", "US", "GB", "JP", "IN",
                    "AU", "CA", "FR", "DE", "ES", "IT", "BR", "NZ", "IE", "MX", "ID",
                ]
                if value in regions
            ],
            "years": years,
            "genres": [{"id": value, "label": value} for value in genres],
            "languages": [],
        },
        "items": items,
        "provenance": {
            "tvmaze": "https://www.tvmaze.com/api",
            "imdb": "https://www.imdb.com/interfaces/",
            "wikipedia": "https://zh.wikipedia.org/w/api.php",
            "scope": "IMDb public ratings with at least 100 votes, 1950-current year, movie/tvSeries/tvMiniSeries/tvMovie/tvSpecial; this is the complete public-rating slice for the declared scope, not a top-score sample. TVmaze full public index pages 0-379 are merged for series/variety ratings and country metadata; Wikidata/Wikipedia enrichment is additive.",
            "tvmazeIndexPolicy": "完整扫描公开索引第 0-379 页；搜索补充用于新近地区条目；失败页面不写入快照，成功页不足完整阈值时不缓存为完整索引。",
            "posterPolicy": "中国制作条目优先补充中文维基公开海报/简介；可复现的高可见度海报进入 APK，其余受控 Wikimedia/TVmaze URL 由客户端安全加载；没有合法图片时保留确定性占位，不伪造来源海报。",
            "localizationPolicy": "优先使用 TVmaze 中文别名、IMDb 中文 AKA、Wikidata 中文标签或中文维基条目；没有可靠中文条目时使用中文缺省简介，不把英文简介泄漏到客户端。",
            "regionFieldPolicy": "countries 优先来自 Wikidata country of origin (P495)，其次为经过审核的标题级回退；IMDb title.akas 的地区/发行市场保存在 releaseMarkets，不用于制作国家筛选。界面以‘制作国家/地区’呈现。",
            "coveragePolicy": "完整 IMDb 评分范围用于分页；TVmaze、Wikidata 与中文维基仅对可匹配条目做附加评分和元数据增强，不把缺少增强误报为缺少作品；默认榜单先将喜剧、动作、恐怖、悬疑、惊悚任一类型的条目置于偏好组，再在组内按贝叶斯加权分排序；票数仅作为置信度向全局先验平滑，原始评分仍单独展示。",
            "asOf": DATA_AS_OF,
        },
    }


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    catalog = build_catalog(build_items())
    # The Android repository reads one bounded snapshot string before parsing.
    # Minifying the checked-in JSON is therefore a runtime memory safeguard,
    # not merely a download-size optimization.
    OUT_FILE.write_text(json.dumps(catalog, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    # The compatibility JSON above remains useful for data review and the
    # asset-quality tests.  Android itself consumes a small metadata index and
    # a streaming JSON-lines payload: parsing one 43 MB JSONObject previously
    # exceeded the normal device heap once the catalogue became genuinely
    # complete.  Keep one item per line so the repository can scan filters
    # without retaining the long tail in memory.
    import hashlib
    digest = hashlib.sha256()
    with OUT_ITEMS_FILE.open("w", encoding="utf-8", newline="\n") as rows:
        for item in catalog["items"]:
            line = json.dumps(item, ensure_ascii=False, separators=(",", ":"))
            rows.write(line)
            rows.write("\n")
            digest.update((line + "\n").encode("utf-8"))
    index = {key: value for key, value in catalog.items() if key != "items"}
    index.update({
        "itemCount": len(catalog["items"]),
        "itemAsset": "ranking/items.ndjson",
        "catalogFingerprint": digest.hexdigest()[:16],
    })
    OUT_INDEX_FILE.write_text(json.dumps(index, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"wrote {OUT_FILE} with {len(catalog['items'])} items")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
