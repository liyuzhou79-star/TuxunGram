package com.tuxungram.android.ui

/**
 * Bundled country table for the login flow (v0.9.21,
 * AB1/AB2): ISO code, Chinese name, English name and the
 * international dial code, kept as one compact string so
 * the whole table costs a few KB and no dependency. The
 * flag emoji is generated from the ISO code (regional
 * indicator symbols) and never stored. Number handling
 * stays the app's own: the picked country's dial code is
 * prepended to the digits the user typed — no phone-number
 * library is involved.
 */
data class Country(
    val iso: String,
    val zhName: String,
    val enName: String,
    val dial: String
) {
    /** Flag emoji for the ISO code, e.g. CN -> 🇨🇳. */
    val flag: String
        get() {
            if (iso.length != 2) return ""
            val sb = StringBuilder()
            for (c in iso.uppercase()) {
                sb.appendCodePoint(0x1F1E6 + (c.code - 'A'.code))
            }
            return sb.toString()
        }
}

object Countries {

    // ISO,中文名,English name,dial code — entries split by ';'.
    private const val RAW =
        "AD,安道尔,Andorra,376;AE,阿联酋,United Arab Emirates,971;" +
        "AF,阿富汗,Afghanistan,93;AG,安提瓜和巴布达,Antigua and Barbuda,1;" +
        "AI,安圭拉,Anguilla,1;AL,阿尔巴尼亚,Albania,355;" +
        "AM,亚美尼亚,Armenia,374;AO,安哥拉,Angola,244;" +
        "AQ,南极洲,Antarctica,672;AR,阿根廷,Argentina,54;" +
        "AS,美属萨摩亚,American Samoa,1;AT,奥地利,Austria,43;" +
        "AU,澳大利亚,Australia,61;AW,阿鲁巴,Aruba,297;" +
        "AX,奥兰群岛,Aland Islands,358;AZ,阿塞拜疆,Azerbaijan,994;" +
        "BA,波斯尼亚和黑塞哥维那,Bosnia and Herzegovina,387;" +
        "BB,巴巴多斯,Barbados,1;BD,孟加拉国,Bangladesh,880;" +
        "BE,比利时,Belgium,32;BF,布基纳法索,Burkina Faso,226;" +
        "BG,保加利亚,Bulgaria,359;BH,巴林,Bahrain,973;" +
        "BI,布隆迪,Burundi,257;BJ,贝宁,Benin,229;" +
        "BM,百慕大,Bermuda,1;BN,文莱,Brunei,673;" +
        "BO,玻利维亚,Bolivia,591;BQ,博奈尔,Bonaire,599;" +
        "BR,巴西,Brazil,55;BS,巴哈马,Bahamas,1;" +
        "BT,不丹,Bhutan,975;BW,博茨瓦纳,Botswana,267;" +
        "BY,白俄罗斯,Belarus,375;BZ,伯利兹,Belize,501;" +
        "CA,加拿大,Canada,1;CD,刚果（金）,DR Congo,243;" +
        "CF,中非共和国,Central African Republic,236;CG,刚果（布）,Congo,242;" +
        "CH,瑞士,Switzerland,41;CI,科特迪瓦,Cote d'Ivoire,225;" +
        "CK,库克群岛,Cook Islands,682;CL,智利,Chile,56;" +
        "CM,喀麦隆,Cameroon,237;CN,中国,China,86;" +
        "CO,哥伦比亚,Colombia,57;CR,哥斯达黎加,Costa Rica,506;" +
        "CU,古巴,Cuba,53;CV,佛得角,Cape Verde,238;" +
        "CW,库拉索,Curacao,599;CY,塞浦路斯,Cyprus,357;" +
        "CZ,捷克,Czechia,420;DE,德国,Germany,49;" +
        "DJ,吉布提,Djibouti,253;DK,丹麦,Denmark,45;" +
        "DM,多米尼克,Dominica,1;DO,多米尼加共和国,Dominican Republic,1;" +
        "DZ,阿尔及利亚,Algeria,213;EC,厄瓜多尔,Ecuador,593;" +
        "EE,爱沙尼亚,Estonia,372;EG,埃及,Egypt,20;" +
        "ER,厄立特里亚,Eritrea,291;ES,西班牙,Spain,34;" +
        "ET,埃塞俄比亚,Ethiopia,251;FI,芬兰,Finland,358;" +
        "FJ,斐济,Fiji,679;FK,福克兰群岛,Falkland Islands,500;" +
        "FM,密克罗尼西亚,Micronesia,691;FO,法罗群岛,Faroe Islands,298;" +
        "FR,法国,France,33;GA,加蓬,Gabon,241;" +
        "GB,英国,United Kingdom,44;GD,格林纳达,Grenada,1;" +
        "GE,格鲁吉亚,Georgia,995;GF,法属圭亚那,French Guiana,594;" +
        "GG,根西,Guernsey,44;GH,加纳,Ghana,233;" +
        "GI,直布罗陀,Gibraltar,350;GL,格陵兰,Greenland,299;" +
        "GM,冈比亚,Gambia,220;GN,几内亚,Guinea,224;" +
        "GP,瓜德罗普,Guadeloupe,590;GQ,赤道几内亚,Equatorial Guinea,240;" +
        "GR,希腊,Greece,30;GT,危地马拉,Guatemala,502;" +
        "GU,关岛,Guam,1;GW,几内亚比绍,Guinea-Bissau,245;" +
        "GY,圭亚那,Guyana,592;HK,香港,Hong Kong,852;" +
        "HN,洪都拉斯,Honduras,504;HR,克罗地亚,Croatia,385;" +
        "HT,海地,Haiti,509;HU,匈牙利,Hungary,36;" +
        "ID,印度尼西亚,Indonesia,62;IE,爱尔兰,Ireland,353;" +
        "IL,以色列,Israel,972;IM,马恩岛,Isle of Man,44;" +
        "IN,印度,India,91;IO,英属印度洋领地,British Indian Ocean Territory,246;" +
        "IQ,伊拉克,Iraq,964;IR,伊朗,Iran,98;" +
        "IS,冰岛,Iceland,354;IT,意大利,Italy,39;" +
        "JE,泽西,Jersey,44;JM,牙买加,Jamaica,1;" +
        "JO,约旦,Jordan,962;JP,日本,Japan,81;" +
        "KE,肯尼亚,Kenya,254;KG,吉尔吉斯斯坦,Kyrgyzstan,996;" +
        "KH,柬埔寨,Cambodia,855;KI,基里巴斯,Kiribati,686;" +
        "KM,科摩罗,Comoros,269;KN,圣基茨和尼维斯,Saint Kitts and Nevis,1;" +
        "KP,朝鲜,North Korea,850;KR,韩国,South Korea,82;" +
        "KW,科威特,Kuwait,965;KY,开曼群岛,Cayman Islands,1;" +
        "KZ,哈萨克斯坦,Kazakhstan,7;LA,老挝,Laos,856;" +
        "LB,黎巴嫩,Lebanon,961;LC,圣卢西亚,Saint Lucia,1;" +
        "LI,列支敦士登,Liechtenstein,423;LK,斯里兰卡,Sri Lanka,94;" +
        "LR,利比里亚,Liberia,231;LS,莱索托,Lesotho,266;" +
        "LT,立陶宛,Lithuania,370;LU,卢森堡,Luxembourg,352;" +
        "LV,拉脱维亚,Latvia,371;LY,利比亚,Libya,218;" +
        "MA,摩洛哥,Morocco,212;MC,摩纳哥,Monaco,377;" +
        "MD,摩尔多瓦,Moldova,373;ME,黑山,Montenegro,382;" +
        "MG,马达加斯加,Madagascar,261;MH,马绍尔群岛,Marshall Islands,692;" +
        "MK,北马其顿,North Macedonia,389;ML,马里,Mali,223;" +
        "MM,缅甸,Myanmar,95;MN,蒙古,Mongolia,976;" +
        "MO,澳门,Macau,853;MP,北马里亚纳群岛,Northern Mariana Islands,1;" +
        "MQ,马提尼克,Martinique,596;MR,毛里塔尼亚,Mauritania,222;" +
        "MS,蒙特塞拉特,Montserrat,1;MT,马耳他,Malta,356;" +
        "MU,毛里求斯,Mauritius,230;MV,马尔代夫,Maldives,960;" +
        "MW,马拉维,Malawi,265;MX,墨西哥,Mexico,52;" +
        "MY,马来西亚,Malaysia,60;MZ,莫桑比克,Mozambique,258;" +
        "NA,纳米比亚,Namibia,264;NC,新喀里多尼亚,New Caledonia,687;" +
        "NE,尼日尔,Niger,227;NG,尼日利亚,Nigeria,234;" +
        "NI,尼加拉瓜,Nicaragua,505;NL,荷兰,Netherlands,31;" +
        "NO,挪威,Norway,47;NP,尼泊尔,Nepal,977;" +
        "NR,瑙鲁,Nauru,674;NU,纽埃,Niue,683;" +
        "NZ,新西兰,New Zealand,64;OM,阿曼,Oman,968;" +
        "PA,巴拿马,Panama,507;PE,秘鲁,Peru,51;" +
        "PF,法属波利尼西亚,French Polynesia,689;PG,巴布亚新几内亚,Papua New Guinea,675;" +
        "PH,菲律宾,Philippines,63;PK,巴基斯坦,Pakistan,92;" +
        "PL,波兰,Poland,48;PR,波多黎各,Puerto Rico,1;" +
        "PS,巴勒斯坦,Palestine,970;PT,葡萄牙,Portugal,351;" +
        "PW,帕劳,Palau,680;PY,巴拉圭,Paraguay,595;" +
        "QA,卡塔尔,Qatar,974;RE,留尼汪,Reunion,262;" +
        "RO,罗马尼亚,Romania,40;RS,塞尔维亚,Serbia,381;" +
        "RU,俄罗斯,Russia,7;RW,卢旺达,Rwanda,250;" +
        "SA,沙特阿拉伯,Saudi Arabia,966;SB,所罗门群岛,Solomon Islands,677;" +
        "SC,塞舌尔,Seychelles,248;SD,苏丹,Sudan,249;" +
        "SE,瑞典,Sweden,46;SG,新加坡,Singapore,65;" +
        "SH,圣赫勒拿,Saint Helena,290;SI,斯洛文尼亚,Slovenia,386;" +
        "SK,斯洛伐克,Slovakia,421;SL,塞拉利昂,Sierra Leone,232;" +
        "SM,圣马力诺,San Marino,378;SN,塞内加尔,Senegal,221;" +
        "SO,索马里,Somalia,252;SR,苏里南,Suriname,597;" +
        "SS,南苏丹,South Sudan,211;ST,圣多美和普林西比,Sao Tome and Principe,239;" +
        "SV,萨尔瓦多,El Salvador,503;SX,荷属圣马丁,Sint Maarten,1;" +
        "SY,叙利亚,Syria,963;SZ,斯威士兰,Eswatini,268;" +
        "TC,特克斯和凯科斯群岛,Turks and Caicos Islands,1;TD,乍得,Chad,235;" +
        "TG,多哥,Togo,228;TH,泰国,Thailand,66;" +
        "TJ,塔吉克斯坦,Tajikistan,992;TL,东帝汶,Timor-Leste,670;" +
        "TM,土库曼斯坦,Turkmenistan,993;TN,突尼斯,Tunisia,216;" +
        "TO,汤加,Tonga,676;TR,土耳其,Turkey,90;" +
        "TT,特立尼达和多巴哥,Trinidad and Tobago,1;TV,图瓦卢,Tuvalu,688;" +
        "TW,台湾,Taiwan,886;TZ,坦桑尼亚,Tanzania,255;" +
        "UA,乌克兰,Ukraine,380;UG,乌干达,Uganda,256;" +
        "US,美国,United States,1;UY,乌拉圭,Uruguay,598;" +
        "UZ,乌兹别克斯坦,Uzbekistan,998;VA,梵蒂冈,Vatican City,379;" +
        "VC,圣文森特和格林纳丁斯,Saint Vincent and the Grenadines,1;" +
        "VE,委内瑞拉,Venezuela,58;VG,英属维尔京群岛,British Virgin Islands,1;" +
        "VI,美属维尔京群岛,US Virgin Islands,1;VN,越南,Vietnam,84;" +
        "VU,瓦努阿图,Vanuatu,678;WS,萨摩亚,Samoa,685;" +
        "XK,科索沃,Kosovo,383;YE,也门,Yemen,967;" +
        "YT,马约特,Mayotte,262;ZA,南非,South Africa,27;" +
        "ZM,赞比亚,Zambia,260;ZW,津巴布韦,Zimbabwe,263"

    /** All countries, sorted by English name (the picker
     *  groups on its first letter, the official way). */
    val all: List<Country> by lazy {
        RAW.split(';').map { entry ->
            val f = entry.split(',')
            Country(f[0], f[1], f[2], f[3])
        }.sortedBy { it.enName }
    }

    fun byIso(iso: String?): Country? {
        if (iso.isNullOrEmpty()) return null
        return all.firstOrNull { it.iso.equals(iso, ignoreCase = true) }
    }

    /** The phone page's starting country: the system
     *  locale's country when the table knows it, China
     *  (+86) otherwise — the same default the login page
     *  has always used. */
    fun inferredDefault(): Country =
        byIso(java.util.Locale.getDefault().country)
            ?: byIso("CN")
            ?: all.first()
}
