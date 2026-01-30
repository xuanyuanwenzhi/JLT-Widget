package com.example.myapplication

import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * WeatherUiText
 *
 * 作用：
 * - 根据当前时间段（早/白天/傍晚/夜间）与概率权重，随机挑一条“语录”
 * - 语录分三类：关心 CARE / 晚安 NIGHT / 天气 WEATHER
 * - 天气类语录会根据天气 JSON（Juhe 或 WTTR）解析出：晴/雨/风/雾霾/AQI/降温/升温 等信号，再在对应池子里随机
 *
 * 设计目标：
 * 1) 大多数时候是“关心语录”（不那么吵的那种）
 * 2) 早上 6~9 且“当天第一次点”强制早安
 * 3) 晚上更容易出晚安/明日天气提示
 *
 */
object WeatherUiText {

    // ===== 语录库（写死在 Kotlin，后面要换 JSON/远端配置再说） =====

    /** 早安语录：仅在 6~9 且 isFirstToday==true 时强制出现 */
    private val MORNING = listOf(
        "早安，慢慢来，今天也会顺顺利利。",
        "早呀，别急，先把今天过好。",
        "新的一天上线，记得吃早饭。",
        "早安～先喝口水，再开始也不迟。",
        "起床成功！今天也要对自己温柔一点。",
        "早上好，愿你今天少点烦，多点顺。",
        "早安，别把所有事都挤在一小时里完成。",
        "醒了就赢一半：先把自己照顾好。"
    )

    /**
     * 日常关心语录：
     * - 这里主要是关心 + 提醒 + 轻鼓励
     * - 量比较大，是默认主力
     */
    private val CARE = listOf(
        // 温柔接纳类
        "累了就歇一会儿，你已经很努力了。",
        "别对自己太严格，慢慢来也算进步。",
        "你很棒，只是今天需要一点耐心。",
        "先把能做的一小步做完，其他的后面再说。",
        "今天要是有点烦，就把节奏放慢一点。",
        "不用一直满分，有个及格就很好了。",
        "别硬撑，饿了吃、困了睡，情绪也一样。",
        "你不是机器，允许自己偶尔卡壳。",
        "如果现在很乱，就先整理桌面或洗把脸。",
        "事情再多，也记得给自己留一点喘气。",
        "先照顾好身体，别的都能慢慢补回来。",
        "遇到糟心事就先停十秒，不要马上硬扛。",
        "今天也许不完美，但你已经在往前走了。",
        "别跟自己较劲，能走到这就很不容易。",
        "如果不想说话也没关系，安静一会儿就好。",
        "再坚持一下也行，先休息一下也行，你说了算。",
        "把注意力放回当下：一口气、一口水、一步路。",
        "你已经做得够多了，允许自己放过自己。",
        "心累的时候就少做决定，先吃饭睡觉。",
        "别着急证明什么，你的存在本身就很重要。",
        "偶尔摆烂也没关系，紧绷的弦总要松一松。",
        "没做成的事不用揪着，放过自己比什么都重要。",
        "就算今天什么都没做，也不算浪费时间。",
        "情绪上来了就顺着它，不用逼自己“快点好”。",
        "不用总想着“应该怎样”，你想怎样就怎样。",
        "卡壳不是你的错，是生活需要缓一缓。",
        "就算进度慢到离谱，也比原地不动强呀。",
        "你不必事事有回应，先回应自己的感受。",
        "今天的难只是暂时的，先接纳这份难就好。",
        "不用逼自己乐观，难过也可以的。",
        "能正视自己的疲惫，就已经很勇敢了。",
        "把“我必须做好”换成“我尽力就好”，会轻松很多。",
        "就算没达到期待，也没人会真的怪你。",
        "允许自己有“摆烂的一天”，这是生活的弹性。",
        "不用和昨天的自己比，今天的你也很好。",
        "乱就乱点吧，生活本就没有标准答案。",

        // 日常暖心类
        "记得喝口水呀，身体暖暖的才舒服。",
        "今天的风很温柔，要不要抬头看看云？",
        "不管忙到几点，都要记得好好吃顿饭。",
        "偶尔偷个懒也没关系，生活本就该有松有紧。",
        "哪怕只做好一件小事，也是超棒的一天。",
        "要是觉得累，就窝在舒服的地方发会儿呆吧。",
        "不用逼自己合群，独处也能很舒服。",
        "今天的你也在认真生活，已经超棒啦。",
        "洗个热水澡，把不开心都冲掉好不好？",
        "慢慢来，生活不会因为慢一点就亏待你。",
        "偶尔忘事没关系，脑子也需要放个假。",
        "不用总想着照顾所有人，先照顾好自己呀。",
        "窗外的阳光很好，要不要伸个懒腰晒一晒？",
        "哪怕没做成什么大事，也值得被好好对待。",
        "听首喜欢的歌吧，让心情跟着旋律松一松。",
        "走得慢一点没关系，方向对了就不怕晚。",
        "记得把脚翘起来歇会儿，别让身体一直紧绷。",
        "泡个脚吧，暖暖的，烦恼都会少一点。",
        "今天的晚霞很好看，要不要停下脚步看一看？",
        "买个喜欢的小零食吧，取悦自己不用等理由。",
        "整理一下抽屉吧，清爽的小空间会治愈心情。",
        "喝杯热饮吧，从嘴巴暖到心里。",
        "不用急着回复消息，先给自己十分钟放空。",
        "把手机调静音，和自己待一会儿好不好？",
        "今天的饭菜多嚼几口，好好感受食物的味道。",
        "换件舒服的衣服吧，身体放松了心情也会软。",
        "闻闻洗衣液的香味，平凡的小事也有温柔呀。",
        "看看路边的小花吧，它也在努力开得好看。",
        "给自己泡杯茶，慢慢喝，不用赶时间。",
        "把窗帘拉开一点，让光进来，心情也会亮一点。",
        "踩踩草地吧，泥土的味道会让人踏实。",
        "和喜欢的人聊两句吧，哪怕只是废话也很好。",
        "整理一下桌面，乱乱的空间会让人更焦虑哦。",
        "吃点水果吧，甜滋滋的，心情也会甜一点。",
        "不用总刷手机，发呆的时间也很珍贵。",
        "坐下来好好喘口气，不用一直往前走。",
        "摸一摸家里的小宠物吧，软乎乎的超治愈。",
        "写两行碎碎念吧，把心事倒出来会轻松。",
        "给自己的杯子接满水，别等渴了才想起喝。",

        // 轻量鼓励类
        "你不用事事都做好，做你自己就够了。",
        "今天的小烦恼，明天就会变成小浮云啦。",
        "每一次小小的坚持，都在让你变更好。",
        "就算没做成想做的事，也没人会怪你的。",
        "你的努力从来都不是白费的，只是还没到时候。",
        "不用和别人比，你有自己的节奏和美好。",
        "哪怕只是好好呼吸，也是在认真生活呀。",
        "允许自己有情绪，哭出来或者笑出来都可以。",
        "你值得所有温柔，包括对自己的温柔。",
        "小小的进步也是进步，别小看自己呀。",
        "停下来不是放弃，是为了更好地往前走。",
        "你不需要完美，真实的你就很可爱。",
        "今天的疲惫，睡一觉就会少一半啦。",
        "做不到的事就放一放，天不会塌下来的。",
        "你已经比昨天的自己更勇敢了，超厉害的。",
        "你走过的每一步，都藏着看不见的收获。",
        "就算结果不如预期，过程里的认真也超珍贵。",
        "你比自己想象中更能扛，只是你还没发现。",
        "不用急着开花，你可以慢慢长成自己的样子。",
        "每一次“没关系”，都是在和自己和解呀。",
        "你认真对待小事的样子，真的超有魅力。",
        "哪怕只是坚持起床，也是今天的小胜利。",
        "你的温柔和善良，都是超棒的闪光点。",
        "不用怕“不够好”，你已经是独一无二的了。",
        "这次没做好，下次我们慢慢来，总会好的。",
        "你能正视自己的不足，就已经赢了大半。",
        "那些默默坚持的日子，都会变成礼物的。",
        "就算走了弯路，也看到了不一样的风景呀。",
        "你的存在，本身就是一件值得庆祝的事。",
        "不用逼自己“成长”，慢慢来，花期自有安排。",
        "每一次放下执念，都是在给自己松绑。",
        "你对自己的包容，就是最好的鼓励呀。",
        "就算没人夸你，我也想告诉你：你超棒的。",
        "能从糟糕的一天里走出来，就已经很厉害。",
        "你的努力，时光都会帮你记着的。",
        "不用追求“快速变好”，一点点变好就够了。",
        "你敢面对不完美的自己，就已经很勇敢了。",

        // 治愈陪伴类（更“陪伴感”，但不是祝福腔）
        "不管发生什么，都有我在这儿陪着你。",
        "不用急着解决所有问题，先感受当下的美好。",
        "就算全世界都催你快一点，我也想让你慢下来。",
        "你的感受最珍贵，不用因为别人委屈自己。",
        "累了就靠一靠，不用一直挺直腰板呀。",
        "生活偶尔有点难，但你比生活更可爱。",
        "把烦恼折成纸飞机，扔出去就不用管啦。",
        "今天的你辛苦了，奖励自己一个甜甜的小零食吧。",
        "哪怕只是发呆，也是在给心灵充电呀。",
        "你不用扛起所有，偶尔示弱也没关系。",
        "慢慢来，时间会把最好的都留给你。",
        "不管走多远，都别忘了回头看看，你已经走了很远啦。",
        "心情不好就吃点甜的，生活总要有点甜呀。",
        "你认真对待生活的样子，真的特别好看。",
        "不用怕犯错，错了就改，改不了就放过自己。",
        "我不会催你，也不会逼你，就安安静静待在你身边。",
        "就算全世界都不理解你，我也愿意听你说废话。",
        "你不用假装坚强，在我这里可以做软软的自己。",
        "那些说不出口的委屈，我都懂，不用憋着。",
        "生活的难我替你分担一点，快乐我们一起多一点。",
        "就算日子有点灰，我也想做你的那一点点光。",
        "不管你选哪条路，我都会站在你这边呀。",
        "你可以把所有的负面情绪，都倒给我没关系。",
        "我不会说“别难过”，我会说“难过就哭吧，我陪着”。",
        "不用急着给出答案，我可以等，等你准备好。",
        "就算你暂时找不到方向，我也会陪着你慢慢找。",
        "你的小情绪不是“矫情”，是值得被在意的呀。",
        "我会接住你的所有不开心，然后陪你慢慢好起来。",
        "不用怕麻烦我，能被你需要，我超开心的。",
        "就算走得再慢，我也会跟在你身后，不催你。",
        "你不用变成任何人，做你自己，我就很喜欢。",
        "那些你觉得“不够好”的地方，我都觉得很可爱。",
        "我会陪着你，把难熬的日子都熬成温柔的时光。",
        "就算今天什么都没做成，我也觉得你超棒的。",
        "你的每一个小小心愿，都值得被认真对待。",
        "我不会说“加油”，我会说“累了就歇，我等你”。",
        "不管未来怎么样，至少此刻我陪着你呀。",
        "你可以停下来，我会在原地，等你想走了再一起。",
        "你的快乐，比什么都重要，真的。"
    )

    /** 晚安语录：夜间概率更高，也可能在 20 点后出现 */
    private val NIGHT = listOf(
        // 温柔收尾类
        "晚安，今天辛苦了，明天再继续。",
        "收工啦，睡前放松一下，晚安。",
        "晚安，把烦心事先放一边。",
        "今天到此为止，剩下的明天再处理。",
        "晚安～别熬太久，明天还要靠你呢。",
        "睡前别想太多，先让大脑休息。",
        "关灯吧，今天已经够努力了。",
        "晚安，愿你今晚睡个踏实觉。",
        "今天不顺也没关系，先把你自己哄好。",
        "晚安，明天的你会比今天更轻松一点。",
        "晚安呀，今天的事就到这，别再琢磨啦。",
        "把今天的疲惫都放下，晚安，好好睡。",
        "不管今天怎么样，睡一觉就翻篇啦，晚安。",
        "结束今天的小忙碌，晚安，愿你好梦。",
        "睡前清空烦恼，晚安，今夜只留温柔。",
        "今天的努力够够的了，晚安，好好休息。",
        "别惦记没做完的事，先睡好，晚安～",
        "给今天画个温柔的句号，晚安啦。",
        "大脑该下班了，晚安，梦里都是甜的。",
        "放下所有紧绷，晚安，今夜只管舒服。",

        // 暖心陪伴类
        "晚安，就算今天没做好也没关系，我都懂。",
        "不用急着赶路，今夜先好好歇脚，晚安。",
        "裹紧被子，把所有不开心都挡在外面，晚安。",
        "睡前摸一摸枕头，告诉自己：今天也很棒，晚安。",
        "不管有没有做成什么，你都值得好好睡觉，晚安。",
        "我陪着你呢，先睡啦，晚安～",
        "今天的小委屈，睡一觉就会消失的，晚安。",
        "不用硬撑啦，睡前做个软软的自己，晚安。",
        "把今天的小遗憾藏好，晚安，明天会有新惊喜。",
        "晚风很温柔，你也该歇歇了，晚安。",

        // 轻量提醒类
        "晚安，手机放远一点，眼睛也要休息呀。",
        "睡前喝口温水，暖暖胃，晚安～",
        "别刷手机啦，被窝里的温柔比屏幕更暖，晚安。",
        "记得拉上窗帘，把噪音和烦恼都隔开，晚安。",
        "枕头调舒服点，今晚要睡个不翻身的觉，晚安。",
        "睡前伸个懒腰，把一天的疲惫都伸走，晚安。",
        "别想明天要做什么，先把今晚睡好，晚安。",
        "空调别开太低，盖好小被子，晚安～",
        "睡前别吃太甜，梦里才不会齁，晚安。",
        "把闹钟调温柔点，晚安，明天不用急着起。",

        // 治愈松弛类
        "晚安，慢慢来，日子总会慢慢变好的。",
        "今夜不催你，不逼你，只祝你睡个好觉，晚安。",
        "就算今天很普通，也是独一无二的一天呀，晚安。",
        "不用追求完美的睡眠，能闭眼歇会儿就很好，晚安。",
        "允许自己什么都不想，就只是睡觉，晚安。",
        "梦里没有 Deadline，只有软软的云，晚安～",
        "今天的你已经满分了，晚安，好好充电。",
        "生活偶尔难，但今夜的床很软，晚安。",
        "把脚步放慢，把呼吸调缓，晚安。",
        "你不用一直发光，今夜可以只做星星，晚安。",

        // 轻甜治愈类
        "晚安，奖励自己一个无梦的好觉✨。",
        "今天的可爱值已超标，晚安，梦里继续可爱。",
        "揣一口袋温柔，钻进被窝，晚安～",
        "月亮都睡了，你也该睡啦，晚安。",
        "星星替我陪你，晚安，今夜好梦。",
        "把烦恼折成纸船，让它随晚风飘走，晚安。",
        "睡前笑一笑，梦里都是小美好，晚安。",
        "今天的小确幸都攒起来，晚安，梦里慢慢尝。",
        "被窝是温柔乡，快进去躲一躲，晚安。",
        "晚安，明天醒来，阳光和好运都在。",

        // 简约温柔类
        "晚安，愿今夜温柔。",
        "好好睡，晚安。",
        "今夜安，明天暖。",
        "睡吧，晚安～",
        "晚安，不负今夜。",
        "歇一歇，晚安。",
        "晚风伴眠，晚安。",
        "心安，晚安。",
        "夜安，万事皆缓。",
        "好梦，晚安。"
    )

    // ===== 天气类语录池 =====
    // 注意：这里的“今天/明天”逻辑只影响 pickWeatherQuote 的解析对象

    /** 晴天池（中英关键词兼容） */
    private val SUNNY = listOf(
        "晴天上线，适合出去透口气。",
        "阳光不错，心情也可以跟着亮一点。",
        "天气挺晴，走路的时候记得抬头看一眼天。",
        "晴天不一定代表顺利，但至少视野更清楚。",
        "有太阳的日子，连空气都像更轻一点。",
        "晴天适合做一件小事：散步、晒被子、晒心情。",
        "阳光洒下来啦，伸个懒腰，今天也会慢慢好起来。",
        "晴天就该把烦恼拿出来晒一晒，说不定就蒸发了。",
        "趁天晴，去买杯喜欢的饮品，边走边晒晒太阳吧。",
        "今天的阳光不刺眼，刚好够温暖你的小情绪。",
        "晴天的风都是软的，要不要去窗边吹一吹？",
        "把窗帘拉开，让阳光住进房间，心情也会敞亮。",
        "晴天不用急着赶路，偶尔停下来看看云也很好。",
        "就算没什么计划，晴天出门走走也超治愈的。",
        "阳光会帮你赶走小阴霾，今天只管放松就好。",
        "天晴啦，把昨天的不开心都留给风，今天要开心。",
        "晴天的影子都变得温柔，你也可以慢下来呀。",
        "找个有阳光的角落坐一坐，什么都不想也没关系。",
        "晴天的温度刚刚好，不用裹太厚，轻松出门吧。",
        "阳光落在身上，像被温柔抱了一下，超舒服的。",
        "晴天适合整理心情，把乱乱的思绪都捋顺。",
        "就算今天没做成大事，晴天本身就值得开心啦。",
        "踩着阳光走路，脚步都能轻快一点～"
    )

    /** 雨天池（今天） */
    private val RAIN = listOf(
        "可能有雨，出门记得带伞。",
        "下雨天路滑，慢点走别赶。",
        "雨天别急，慢慢走也没关系。",
        "如果要下雨，鞋子和心情都尽量别湿透。",
        "雨天更容易累，今天把要求放低一点。",
        "下雨就下雨吧，你稳稳走就行。",
        "雨声是温柔的背景音，不用急着回应任何人。",
        "带好伞哦，别让雨打湿头发，会着凉的。",
        "雨天适合窝在屋里，喝杯热饮，听首喜欢的歌。",
        "就算被雨困住也没关系，刚好偷个懒歇一歇。",
        "雨天的路有点滑，每一步都走稳，不赶时间。",
        "雨下得再大，也会停的，就像烦心事一样呀。",
        "出门记得穿防水鞋，别让冰冷的雨水沾到脚。",
        "雨天的空气很清新，开窗透透气，心情会好一点。",
        "不用抱怨下雨，换个角度，听听雨声也很治愈。",
        "如果忘带伞了，找个地方躲躲，别硬淋着哦。",
        "雨天的节奏该慢一点，做事不用追求快，做完就好。",
        "把伞撑稳，就算风雨有点急，也能稳稳往前走。",
        "雨天容易emo，记得给自己找点甜的吃吃～",
        "雨打在窗户上的样子，其实也蛮可爱的呀。",
        "就算出门被雨浇到，也没关系，回家洗个热水澡就好。",
        "雨天不用逼自己出门，宅家也是超棒的选择。",
        "听着雨声睡觉，会睡得更香哦，今晚试试吧。"
    )

    /**
     * 风天池
     * 注意：这里写的是“4级以上”语感，
     * 实际触发条件取决于 parseWeather 得出的 windLevel
     */
    private val WIND = listOf(
        "风有点大，出门注意保暖，帽子别飞了。",
        "大风天别硬刚，能躲就躲一下风。",
        "风到4级以上了，出门把外套拉链拉好。",
        "风大别骑太快，安全第一。",
        "今天风挺冲，眼睛不舒服就少吹风。",
        "大风天走路别贴树边，绕一绕更稳。",
        "风刮得有点猛，出门把围巾系紧，别灌冷风。",
        "大风天尽量别穿宽松的衣服，容易被吹得慌。",
        "骑车的话记得扶稳车把，风大容易晃，慢慢来。",
        "头发容易被吹乱？没关系，乱一点也超好看的。",
        "大风天别玩手机走路，风会迷眼，注意脚下哦。",
        "出门前检查下口袋里的东西，别被风吹跑啦。",
        "风大的时候，呼吸慢一点，别呛到冷风。",
        "就算风大，也别皱眉头，裹紧外套就好啦。",
        "大风天尽量走背风的路，能少受点罪～",
        "戴眼镜的话，记得擦干净镜片，风会吹起灰尘。",
        "风大不适合长时间在外，办完事儿就早点回家。",
        "大风天的阳光再暖，也记得多穿一件，别着凉。",
        "别因为风大就烦躁，等风停了，天会更蓝的。",
        "走路的时候稍微低头，别让风直吹着脸，不舒服。",
        "大风天晾的衣服记得夹牢，别被吹跑啦。",
        "风大的时候，脚步放慢，稳一点比快一点重要。",
        "就算风把头发吹乱，也不用急着整理，随性就好。"
    )

    /** 文案级“霾”触发池：天气描述/实时描述包含“霾”或英文 haze/smog */
    private val HAZE_TEXT = listOf(
        "看着像有雾霾，能戴口罩就戴上。",
        "雾霾天尽量少折腾，回家早点休息。",
        "像是有霾，出门尽量避开大马路边。",
        "雾霾天别逞强运动，轻一点就好。",
        "雾霾天空气差，出门时间别太长，速去速回。",
        "有霾的话，回家记得用温水洗洗脸和鼻子～",
        "雾霾天别开窗通风，免得脏空气跑进屋里。",
        "霾天能见度差，走路别玩手机，注意脚下哦。",
        "雾霾天容易嗓子干，多喝温水润润喉吧。",
        "有霾的日子，尽量别去人多又不通风的地方。",
        "雾霾天出门，戴个护目镜，别让灰尘进眼睛。",
        "霾天就算不出门，也记得多喝点水，保持湿润。",
        "雾霾天别晨练啦，等空气质量好点再动。",
        "有霾的话，包里备包湿纸巾，随时擦擦手。",
        "雾霾天的阳光也不顶用，别想着晒太阳能散霾哦。",
        "霾天回家，外套记得挂在门口，别带进卧室。",
        "雾霾天容易犯困，别硬撑，累了就歇会儿。",
        "有霾的日子，煮点梨水喝，润润呼吸道～",
        "雾霾天尽量坐室内交通工具，少走路吹风。",
        "就算习惯不戴口罩，霾天也别偷懒，护住自己呀。"
    )

    /** 文案级“雾”触发池：天气描述/实时描述包含“雾”或英文 fog/mist */
    private val FOG_TEXT = listOf(
        "看着有雾，出门视线不好，慢点走。",
        "雾天能见度低，骑车/走路都别赶哦。",
        "有雾的话，出门记得带好口罩和眼镜。",
        "雾天空气湿冷，注意保暖，别着凉啦。",
        "雾天尽量别去空旷的地方，就近活动就好。",
        "雾天开车/走路多留意，安全第一呀。",
        "大雾天别开电动车太快，能见度太低啦。",
        "雾天的湿气重，出门记得带把折叠伞，挡挡雾水。",
        "有雾的早晨，别早起出门，等雾散散再动。",
        "雾天路面容易结露变滑，走路脚步放轻放稳。",
        "大雾天别靠路边走，怕被车辆看不到哦。",
        "雾天空气湿冷，出门多穿件防风外套，不钻风。",
        "有雾的话，戴口罩不仅防雾，还能挡湿冷空气。",
        "雾天开车记得开雾灯，别用远光灯，反而看不清。",
        "薄雾天也别掉以轻心，视线还是会受影响的～",
        "雾天回家，鞋子上的水汽擦干净，别弄湿地板。",
        "有雾的日子，别去河边/湖边走，视线差不安全。",
        "雾天容易让人闷，待一会儿就开窗透透气（别开太大）。",
        "大雾天尽量结伴出门，别一个人走偏僻的路。",
        "雾天就算着急赶路，也别跑，稳一点比快一点重要。"
    )

    /** AQI 触发池：当 aqi >= 150（主要来自 Juhe） */
    private val HAZE_AQI = listOf(
        "空气指数不太友好，出门注意防护。",
        "今天空气质量一般般，尽量少在路边久待。",
        "AQI 有点高，口罩/眼镜能用就用上。",
        "空气不太干净，回家记得洗脸洗鼻子更舒服。",
        "AQI超标啦，尽量待在室内，别出门晃悠。",
        "空气质量差，出门别做剧烈运动，走走就好。",
        "空气指数不好，给孩子/老人备个专用口罩吧。",
        "AQI高的日子，别去公园/广场这些开阔地。",
        "空气质量差，出门回来记得换件干净衣服。",
        "AQI超标，就算短时间出门，也别摘口罩哦。",
        "空气不太好，多喝水，多吃点新鲜蔬果。",
        "AQI偏高的下午，也别开窗，等傍晚再看看。",
        "空气质量差，别在阳台晾衣服，容易沾灰尘。",
        "AQI高的话，通勤尽量选地铁，少坐公交开窗。",
        "空气指数不友好，别用嘴巴呼吸，用鼻子哦。",
        "AQI超标，就算室内，也记得多通通风（选净化器）。",
        "空气质量差，嗓子不舒服就别吃辣的，清淡点。",
        "AQI偏高的日子，早点回家，别在外边逗留。",
        "空气不干净，睡前洗个澡，把一天的灰尘都洗掉～"
    )

    /** 明天最低温显著下降（<= -3℃）时用 */
    private val COOL_MIN = listOf(
        "明天最低温会降，记得多穿一件。",
        "夜里更冷了，别被低温偷袭。",
        "最低温要下去，围巾/外套提前准备好。",
        "明早可能更冷，起床别硬扛，穿暖一点。",
        "夜间最低温降了不少，睡觉记得盖厚被子。",
        "明早出门会冻手哦，手套记得揣兜里～",
        "最低温跌破舒适区了，秋裤可以安排上啦。",
        "凌晨温度会更低，起夜记得披件外套。",
        "最低温降得有点快，别只穿单鞋，换双厚的。",
        "夜里的风更凉，窗户别开太大，免得着凉。",
        "明早的低温会打透衣服，内层多穿一件更暖。",
        "最低温降了，出门把领口捂好，别灌冷风。",
        "就算白天不冷，早晚最低温低，也别偷懒少穿。",
        "低温天别露脚踝啦，暖暖和和的才舒服。",
        "明早出门，口罩记得戴，防风又保暖。",
        "最低温降了，早餐喝杯热的，暖暖胃再出门。",
        "夜里温度低，手机别放床头，手伸出去会凉～",
        "最低温走低，出门遛弯别太久，速去速回。",
        "明早的冷空气有点猛，穿件加绒的外套吧。",
        "就算不怕冷，最低温降了也别硬撑，多穿点没亏吃。"
    )

    /** 明天平均温显著下降（<= -3℃）时用 */
    private val COOL_AVG = listOf(
        "明天整体更凉，记得保暖别逞强。",
        "体感可能会变冷，外套安排上。",
        "明天平均温度要降，今天的衣服别照搬。",
        "整体降温，别只看中午暖，早晚更关键。",
        "全天平均温都降了，出门整套穿搭都要加厚。",
        "体感温度比实际还凉，别穿太单薄啦。",
        "整体降温的日子，多喝热水，别让身体受凉。",
        "明天一整天都偏冷，包里揣个暖手宝吧。",
        "平均温降了，就算在室内，也别总穿短袖。",
        "降温天别吃太多凉的，肠胃会不舒服哦。",
        "全天温度都不高，出门把帽子也戴上，护住头。",
        "平均温降了好几度，通勤路上多穿一层更稳。",
        "别觉得中午能回暖就少穿，整体降温躲不过的～",
        "降温天容易犯困，别受凉，不然更没精神。",
        "平均温走低，洗衣服记得换厚的，别穿薄款。",
        "全天都凉飕飕的，办公桌上备件小外套吧。",
        "降温不是一点点，别硬扛，该加衣就加衣。",
        "平均温降了，出门办事别磨蹭，早点回暖和的地方。",
        "就算待在车里，也记得开暖风，别冻着。",
        "整体降温的日子，脚步慢一点，别让冷风钻空子。"
    )

    /** 明天平均温显著上升（>= +3℃）时用 */
    private val WARM_AVG = listOf(
        "明天会暖一些，出门别穿太厚免得闷。",
        "温度要回升啦，心情也可以跟着松一点。",
        "明天平均温度上来点，衣服可以轻一点。",
        "会回暖，但早晚可能还有点凉，分层穿更稳。",
        "全天平均温升了，外套可以换成薄一点的啦。",
        "温度回暖啦，终于不用裹成粽子出门了～",
        "平均温上来了，里面穿件薄毛衣就够啦。",
        "回暖天别一下子脱太多，慢慢来更稳妥。",
        "温度升了，出门可以带件薄外套，热了能脱。",
        "全天都暖和起来了，终于能开窗透透气啦。",
        "平均温回升，鞋子可以换单鞋，不用穿棉的啦。",
        "回暖啦，终于能出门晒晒太阳，不用缩手缩脚了。",
        "温度升了，但室内还凉，别着急脱保暖衣。",
        "平均温上来了，包里别塞太多衣服，轻装上阵～",
        "回暖天容易出汗，内层穿透气的，不闷汗。",
        "全天温度都友好，终于能穿喜欢的薄外套了。",
        "温度回升，出门可以带杯凉饮啦（别喝太多哦）。",
        "平均温升了，早晚还是有点凉，围巾可以备着。",
        "回暖的日子，心情也跟着亮堂，多出门走走吧。",
        "就算平均温升了，也别露太多，防风还是要的～"
    )

    /** 明天可能下雨专属池：tomorrow==true 且识别为雨时叠加 */
    private val RAIN_TOMORROW = listOf(
        "明天可能下雨，记得提前把伞装好哦。",
        "明天要下雨啦，出门前检查下伞在不在包里～",
        "明天有雨，鞋子记得换防水的，别弄湿脚。",
        "预报说明天会下雨，通勤路上别赶，慢点走。",
        "明天大概率下雨，出门多带件薄外套，别着凉。",
        "明天要下雨啦，睡前把伞放门口，早上出门不慌。",
        "明天有雨，要是骑车的话，记得穿件雨衣更稳妥。",
        "预报说明天有雨，别忘带伞，心情也别被雨影响呀。",
        "明天会下雨，出门尽量选室内交通，少淋雨～",
        "明天有雨，早上出门早一点，别因为躲雨迟到啦。",
        "明天要下雨啦，包里备个防水袋，别弄湿手机。",
        "明天可能下雨，就算带了伞，也记得走慢点，路滑。",
        "预报说明天有雨，晚上把窗户关好，别进雨水哦。",
        "明天有雨，要是忘带伞了，记得找地方躲躲，别硬淋。",
        "明天要下雨啦，喝杯热饮再出门，暖暖身子。",
        "明天可能下雨，不用烦，雨天也可以慢慢走呀。",
        "明天有雨，出门前把裤脚挽一点，别溅上泥水。",
        "预报说明天会下雨，给自己留够出门时间，不慌不忙。",
        "明天要下雨啦，就算被雨打湿一点，也没关系的～",
        "明天有雨，记得带把大点的伞，把自己护好。",
        "明天可能下雨，宅家的话就听听雨声，也超治愈的。"
    )

    // ===== 核心：按时间段 + 概率，先决定“语录类型”，再从对应池随机 =====

    /**
     * 选一句语录：
     * @param rawJson 天气缓存 JSON（Juhe / WTTR 均可）
     * @param preferTomorrowWeather 外部偏好：是否倾向用“明天的天气”来挑天气文案
     * @param isFirstToday 是否“今天第一次点击”（用于早安强制）
     */
    fun pickQuote(rawJson: String, preferTomorrowWeather: Boolean, isFirstToday: Boolean): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        // 规则 1：早上 6~9 且今天第一次点击，强制早安（不走概率）
        if (hour in 6..9 && isFirstToday) {
            return MORNING.random()
        }

        /**
         * 规则 2：根据时间段设置权重（你之前定的）
         *
         * - 6~17：白天偏 CARE（80%），少量 WEATHER（20%）
         * - 18~19：傍晚 CARE 70% + WEATHER 30%，且天气倾向“明天”
         * - 20~21：晚间 CARE/WEATHER/NIGHT = 40/30/30，且天气倾向“明天”
         * - 其余：深夜/凌晨 NIGHT 概率更高（50%）
         */
        val bucket = when {
            hour in 6..17 -> Bucket(care = 0.80, weather = 0.20, night = 0.0, weatherTomorrow = false)
            hour in 18..19 -> Bucket(care = 0.70, weather = 0.30, night = 0.0, weatherTomorrow = true)
            hour in 20..21 -> Bucket(care = 0.40, weather = 0.30, night = 0.30, weatherTomorrow = true)
            else -> Bucket(care = 0.20, weather = 0.30, night = 0.50, weatherTomorrow = true)
        }

        // bucket 指定“夜里天气看明天” + 外部 preferTomorrowWeather 任一满足就用明天
        val useTomorrowForWeather = bucket.weatherTomorrow || preferTomorrowWeather

        // 规则 3：roll 一个随机数，决定本次出 CARE / WEATHER / NIGHT 哪种
        val roll = Random.nextDouble()
        val pickType = when {
            roll < bucket.care -> PickType.CARE
            roll < bucket.care + bucket.weather -> PickType.WEATHER
            else -> PickType.NIGHT
        }

        // 规则 4：返回具体文案
        return when (pickType) {
            PickType.CARE -> CARE.random()
            PickType.NIGHT -> NIGHT.random()
            // 天气文案可能因为 JSON 解析失败返回 null；兜底用 CARE
            PickType.WEATHER -> pickWeatherQuote(rawJson, useTomorrowForWeather) ?: CARE.random()
        }
    }

    /**
     * 根据天气信息挑选“天气文案”
     * @param rawJson 天气缓存 JSON
     * @param tomorrow true 表示用“明天”信息；false 表示用“当前/今天”信息
     * @return 匹配的天气文案；若无法解析/无匹配池则返回 null
     */
    private fun pickWeatherQuote(rawJson: String, tomorrow: Boolean): String? {
        val info = parseWeather(rawJson, tomorrow) ?: return null

        // pool = 本次可用的候选文案池（会按条件把多个 list 加进来）
        val pool = mutableListOf<String>()

        // weather：用于触发（可能是“明天预报”或“当前描述”）
        // realtimeInfo：当前实时描述（主要 Juhe realtime.info / WTTR nowDesc）
        val w = info.weather
        val r = info.realtimeInfo

        val lowerW = w.lowercase(Locale.getDefault())
        val lowerR = r.lowercase(Locale.getDefault())

        // 1) 晴：中文“晴”或英文 sun/clear（兼容 w 与 r）
        if (
            w.contains("晴") ||
            containsAny(lowerW, listOf("sun", "clear")) ||
            containsAny(lowerR, listOf("sun", "clear"))
        ) {
            pool += SUNNY
        }

        // 2) 雨：中文“雨”或英文 rain/drizzle/shower/thunder（兼容 w 与 r）
        val isRain = w.contains("雨") ||
                containsAny(lowerW, listOf("rain", "drizzle", "shower", "thunder")) ||
                containsAny(lowerR, listOf("rain", "drizzle", "shower", "thunder"))

        if (isRain) {
            // 明天雨：把“明天雨专属” + “通用雨”都加进 pool（更丰富）
            if (tomorrow) {
                pool += RAIN_TOMORROW
                pool += RAIN
            } else {
                pool += RAIN
            }
        }

        /**
         * 3) 风：
         * - 这里沿用 info.windLevel >= 4 判定
         * - 注意：WTTR 的 windLevel 是 windspeedKmph 粗略映射（见 windLevelFromKmph）
         * - Widget 那边你已经改成更严格阈值判定 WIND 场景
         */
        if (info.windLevel >= 4) pool += WIND

        // 4) 雾霾：中文“霾/雾” 或英文 haze/smog/fog/mist
        val haze =
            w.contains("霾") || r.contains("霾") ||
                    containsAny(lowerW, listOf("haze", "smog")) ||
                    containsAny(lowerR, listOf("haze", "smog"))

        val fog =
            w.contains("雾") || r.contains("雾") ||
                    containsAny(lowerW, listOf("fog", "mist")) ||
                    containsAny(lowerR, listOf("fog", "mist"))

        if (haze) pool += HAZE_TEXT
        if (fog) pool += FOG_TEXT

        // 5) AQI：仅 Juhe 一般有（WTTR 默认 aqi=0）
        if (info.aqi >= 150) pool += HAZE_AQI

        /**
         * 6) 温度变化趋势：只在 tomorrow=true 时比较“明日 vs 今日”
         * - minDiff：明天最低温 - 今天最低温（<= -3 触发 COOL_MIN）
         * - avgDiff：明天平均温 - 今天平均温（<= -3 触发 COOL_AVG，>= +3 触发 WARM_AVG）
         */
        if (
            tomorrow &&
            info.todayMin != null && info.tomorrowMin != null &&
            info.todayAvg != null && info.tomorrowAvg != null
        ) {
            val minDiff = info.tomorrowMin - info.todayMin
            val avgDiff = info.tomorrowAvg - info.todayAvg

            if (minDiff <= -3) pool += COOL_MIN
            if (avgDiff <= -3) pool += COOL_AVG
            if (avgDiff >= 3) pool += WARM_AVG
        }

        // 无任何匹配池：返回 null，让上层兜底 CARE
        if (pool.isEmpty()) return null

        // 最终从合并后的池子里随机一条
        return pool.random()
    }

    // ===== JSON 解析：自动识别 Juhe / WTTR =====

    /**
     * Bucket：某个时间段的概率配置
     * - care / weather / night 三者加起来应为 1.0（你手工配置的）
     * - weatherTomorrow 表示该时段天气更倾向“明天”
     */
    private data class Bucket(
        val care: Double,
        val weather: Double,
        val night: Double,
        val weatherTomorrow: Boolean
    )

    /** 本次 pickQuote 最终会落到哪一类 */
    private enum class PickType { CARE, WEATHER, NIGHT }

    /**
     * Wx：统一后的“可用天气信息”结构
     * - weather：用于分类判断的描述（可能是明天预报，也可能是当前描述）
     * - realtimeInfo：实时描述（主要用于中文/英文关键字兜底）
     * - aqi：空气质量（Juhe 可能有，WTTR 默认 0）
     * - windLevel：一个“粗略风级”（Juhe 从 power 提取；WTTR 从 km/h 粗略映射）
     * - todayMin/todayAvg/tomorrowMin/tomorrowAvg：温度趋势计算用
     */
    private data class Wx(
        val weather: String,
        val realtimeInfo: String,
        val aqi: Int,
        val windLevel: Int,
        val todayMin: Int?,
        val todayAvg: Int?,
        val tomorrowMin: Int?,
        val tomorrowAvg: Int?
    )

    /**
     * parseWeather：把 rawJson 解析成 Wx（兼容 Juhe / WTTR）
     * @param tomorrow true=拿“明天信息”做分类；false=拿“当前/今天信息”
     */
    private fun parseWeather(rawJson: String, tomorrow: Boolean): Wx? {
        if (rawJson.isBlank()) return null

        /**
         * 1) 尝试 Juhe：
         * - Juhe 的 JSON 顶层通常有 error_code
         * - error_code==0 才是成功
         */
        runCatching {
            val root = JSONObject(rawJson)
            if (root.has("error_code")) {
                if (root.optInt("error_code", -1) != 0) return null

                val result = root.optJSONObject("result") ?: return null

                // realtime：当前实时信息
                val realtime = result.optJSONObject("realtime")
                val rtInfo = realtime?.optString("info", "").orEmpty()
                val aqi = realtime?.optString("aqi", "")?.toIntOrNull() ?: 0
                val power = realtime?.optString("power", "").orEmpty()
                val windLevel = parseWindLevel(power)

                // future：未来预报（数组，0=今天，1=明天）
                val future = result.optJSONArray("future") ?: return null
                if (future.length() <= 0) return null

                val todayObj = future.optJSONObject(0)
                val tomObj = if (future.length() > 1) future.optJSONObject(1) else null

                // temperature 格式一般是 "1/17℃"（min/max）
                val todayTemp = todayObj?.optString("temperature", "").orEmpty()
                val tomTemp = tomObj?.optString("temperature", "").orEmpty()

                val (todayMin, todayMax) = parseMinMax(todayTemp)
                val (tomMin, tomMax) = parseMinMax(tomTemp)

                val todayAvg = avg(todayMin, todayMax)
                val tomAvg = avg(tomMin, tomMax)

                // weather：今天/明天的天气描述（比如“晴”“小雨”）
                val weatherStr = if (tomorrow) {
                    tomObj?.optString("weather", "")?.trim().orEmpty()
                } else {
                    todayObj?.optString("weather", "")?.trim().orEmpty()
                }

                return Wx(
                    // 如果预报 weather 为空，就退回 realtime.info
                    weather = weatherStr.ifBlank { rtInfo },
                    realtimeInfo = rtInfo,
                    aqi = aqi,
                    windLevel = windLevel,
                    todayMin = todayMin,
                    todayAvg = todayAvg,
                    tomorrowMin = tomMin,
                    tomorrowAvg = tomAvg
                )
            }
        }

        /**
         * 2) 尝试 WTTR：
         * - WTTR 顶层一般有 current_condition（数组）
         * - 预报在 weather（数组，0=今天，1=明天）
         */
        return runCatching {
            val obj = JSONObject(rawJson)

            // current_condition：当前实时描述
            val cc = obj.optJSONArray("current_condition")?.optJSONObject(0) ?: return null
            val zhNow = cc.optJSONArray("lang_zh")?.optJSONObject(0)?.optString("value").orEmpty()
            val enNow = cc.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
            val nowDesc = if (zhNow.isNotBlank()) zhNow else enNow

            // windspeedKmph：WTTR 的风速（km/h），这里做一个粗略映射成“风级”
            val windKmph = cc.optString("windspeedKmph", "")?.toIntOrNull() ?: 0
            val windLevel = windLevelFromKmph(windKmph)

            // weather：预报数组（每天一个对象）
            val weatherArr = obj.optJSONArray("weather") ?: return null
            val today = weatherArr.optJSONObject(0)
            val tom = if (weatherArr.length() > 1) weatherArr.optJSONObject(1) else null

            // WTTR 预报温度字段：mintempC / maxtempC
            val todayMin = today?.optString("mintempC", "")?.toIntOrNull()
            val todayMax = today?.optString("maxtempC", "")?.toIntOrNull()
            val tomMin = tom?.optString("mintempC", "")?.toIntOrNull()
            val tomMax = tom?.optString("maxtempC", "")?.toIntOrNull()

            val todayAvg = avg(todayMin, todayMax)
            val tomAvg = avg(tomMin, tomMax)

            /**
             * WTTR 的“明天描述”：
             * - 如果 tomorrow=true：尽量取“明天中午 12:00 的描述”（更接近白天体感）
             * - 取不到就用 nowDesc 兜底
             * - 如果 tomorrow=false：直接用 nowDesc
             */
            val weatherStr = if (tomorrow) {
                pickWttrNoonDesc(tom).orEmpty().ifBlank { nowDesc }
            } else {
                nowDesc
            }

            Wx(
                weather = weatherStr,
                realtimeInfo = nowDesc,
                aqi = 0, // WTTR 不提供 AQI（这里默认 0）
                windLevel = windLevel,
                todayMin = todayMin,
                todayAvg = todayAvg,
                tomorrowMin = tomMin,
                tomorrowAvg = tomAvg
            )
        }.getOrNull()
    }

    /**
     * WTTR 风速（km/h） -> 粗略“风级”映射
     * - 只是用于文案触发，不要当专业风级
     *
     * 你之前的逻辑是“>=25km/h 算 4级以上”
     * - 这会比 Widget 那边的“>=30km/h 才算 WIND 场景”更敏感一点
     */
    private fun windLevelFromKmph(kmph: Int): Int {
        return when {
            kmph >= 25 -> 4
            kmph >= 18 -> 3
            kmph >= 12 -> 2
            kmph >= 6 -> 1
            else -> 0
        }
    }

    /**
     * WTTR：尽量取“明天中午 12:00”的天气描述
     * - hourly 是数组，每项有 time（"0"~"2300"）等
     * - 优先找 time=="1200"
     * - 找不到就退回 hourly[0]
     */
    private fun pickWttrNoonDesc(dayObj: JSONObject?): String? {
        if (dayObj == null) return null
        val hourly = dayObj.optJSONArray("hourly") ?: return null

        // 优先找中午 12:00
        for (i in 0 until hourly.length()) {
            val h = hourly.optJSONObject(i) ?: continue
            if (h.optString("time", "") == "1200") {
                val zh = h.optJSONArray("lang_zh")?.optJSONObject(0)?.optString("value").orEmpty()
                val en = h.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
                return if (zh.isNotBlank()) zh else en
            }
        }

        // 找不到就用第一个小时的描述兜底
        val h0 = hourly.optJSONObject(0) ?: return null
        val zh = h0.optJSONArray("lang_zh")?.optJSONObject(0)?.optString("value").orEmpty()
        val en = h0.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
        return if (zh.isNotBlank()) zh else en
    }

    /**
     * 判断 textLower 中是否包含 keys 里的任意关键字
     * - 这个函数默认传入的 textLower 已经是 lowercased 的英文串
     */
    private fun containsAny(textLower: String, keys: List<String>): Boolean {
        for (k in keys) if (textLower.contains(k)) return true
        return false
    }

    /**
     * Juhe：把 realtime.power 的风级提取出来
     * 示例："3级" / "4级" / "3-4级"
     * - 取第一个数字即可
     */
    private fun parseWindLevel(power: String): Int {
        val m = Regex("""(\d+)""").find(power) ?: return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }

    /**
     * Juhe：把 "1/17℃" 解析成 (min, max)
     * - 返回 Pair<Int?, Int?>，解析失败就 (null, null)
     */
    private fun parseMinMax(t: String): Pair<Int?, Int?> {
        val cleaned = t.replace("℃", "").trim()
        val parts = cleaned.split("/")
        if (parts.size != 2) return null to null
        val min = parts[0].trim().toIntOrNull()
        val max = parts[1].trim().toIntOrNull()
        return min to max
    }

    /**
     * 求平均温（四舍五入）
     * - 用于“明日 vs 今日”趋势判断
     */
    private fun avg(a: Int?, b: Int?): Int? {
        if (a == null || b == null) return null
        return ((a + b) / 2.0).roundToInt()
    }
}
