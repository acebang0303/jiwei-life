// 吉味生活 · 大规模 mock 数据生成脚本
// 用法: node db/generate_mock_data.mjs   → 生成 db/98_mock_data.sql
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));

// ============ 工具 ============
const esc = (s) => String(s).replace(/'/g, "''");
const q = (s) => `'${esc(s)}'`;
const rnd = (n) => Math.floor(Math.random() * n);
const pick = (arr) => arr[rnd(arr.length)];
const rand = (min, max) => min + Math.random() * (max - min);
const randInt = (min, max) => Math.floor(rand(min, max + 1));
const price = (min, max) => (rand(min, max)).toFixed(2);

// 日期随机（2026-07 ~ 2026-08）
const dt = () => {
  const m = pick(['07', '08']);
  const d = String(randInt(1, 28)).padStart(2, '0');
  const h = String(randInt(0, 23)).padStart(2, '0');
  const mi = String(randInt(0, 59)).padStart(2, '0');
  return `2026-${m}-${d} ${h}:${mi}:00`;
};

// ============ 菜系菜品库 ============
const cuisines = [
  {
    type: '川菜', merchants: ['蜀香园', '巴蜀人家', '川江号子'],
    dishes: [
      ['水煮鱼', '鲜活草鱼现杀，秘制麻辣红油，鱼片嫩滑入口即化'],
      ['水煮牛肉', '精选牛腱肉，花椒辣椒双重刺激，麻辣鲜香'],
      ['麻婆豆腐', '经典川味，嫩豆腐配牛肉末，麻辣烫鲜嫩'],
      ['回锅肉', '二刀肉配蒜苗，郫县豆瓣炒出灯盏窝，肥而不腻'],
      ['宫保鸡丁', '鸡腿肉丁配花生米，糊辣荔枝味，甜酸微辣'],
      ['辣子鸡', '鸡块炸至金黄，与干辣椒同炒，外酥里嫩'],
      ['夫妻肺片', '牛杂薄片，红油花椒面凉拌，麻辣爽口'],
      ['口水鸡', '白切鸡淋红油，集麻辣鲜香嫩于一身'],
      ['毛血旺', '鸭血毛肚黄喉一锅煮，麻辣浓郁下饭'],
      ['酸菜鱼', '黑鱼片配老坛酸菜，酸辣开胃汤鲜味美'],
      ['干煸四季豆', '四季豆煸至虎皮，肉末芽菜提香'],
      ['鱼香肉丝', '鱼香味型代表，肉丝嫩滑酸甜微辣'],
      ['蒜泥白肉', '五花肉薄片配蒜泥红油，肥而不腻'],
      ['辣子肥肠', '肥肠炸至酥脆，干辣椒花椒爆香'],
      ['钵钵鸡', '冷锅串串，藤椒红油浸泡，麻辣鲜香'],
      ['灯影牛肉', '牛肉片薄如纸，麻辣回甜，佐酒佳品'],
    ],
  },
  {
    type: '火锅', merchants: ['蜀大侠火锅', '九宫格老火锅', '鸳鸯锅物语'],
    dishes: [
      ['鲜毛肚', '屠场直供鲜毛肚，七上八下十五秒，脆嫩化渣'],
      ['鲜鸭肠', '新鲜鸭肠，涮八秒即食，爽脆弹牙'],
      ['雪花肥牛', '大理石纹路，涮五秒即食，奶香浓郁'],
      ['手打虾滑', '青虾仁手打上劲，Q弹鲜甜'],
      ['午餐肉', '厚切午餐肉，涮煮后软糯入味'],
      ['鲜鸭血', '嫩滑鸭血，入口即化，麻辣汤底绝配'],
      ['黄喉', '猪黄喉脆嫩，久煮不老'],
      ['鲜脑花', '新鲜猪脑，麻辣汤底去腥，嫩如豆腐'],
      ['牛肉丸', '手打牛肉丸，弹牙爆汁'],
      ['鱼丸', '鲜鱼肉制丸，清甜弹嫩'],
      ['豆腐皮', '豆香浓郁，吸饱汤汁'],
      ['金针菇', '鲜嫩金针菇，涮煮后爽滑'],
      ['娃娃菜', '清甜娃娃菜，解腻神器'],
      ['土豆片', '粉糯土豆片，煮至软烂更佳'],
      ['藕片', '脆嫩藕片，麻辣入味'],
    ],
  },
  {
    type: '烧烤', merchants: ['老地方烧烤', '火一把烤串', '深夜烤场'],
    dishes: [
      ['羊肉串', '呼伦贝尔草原羊肉，肥瘦相间，炭火现烤'],
      ['牛肉串', '精选牛里脊，秘制腌料，嫩滑多汁'],
      ['烤鸡翅', '蜜汁奥尔良风味，外焦里嫩'],
      ['烤鱿鱼', '整条大鱿鱼，铁板现烤，鲜香弹牙'],
      ['蒜蓉烤生蚝', '湛江大生蚝，蒜蓉粉丝蒸烤，肥美多汁'],
      ['烤茄子', '整条茄子炭烤，蒜蓉辣椒铺面，软糯入味'],
      ['烤韭菜', '韭菜炭火快烤，蒜蓉辣椒提味'],
      ['烤玉米', '甜玉米炭烤，焦香微甜'],
      ['烤鸡爪', '卤制后炭烤，软糯脱骨，胶质丰富'],
      ['烤五花肉', '五花肉切薄片，炭火烤至焦脆，包生菜一绝'],
      ['烤金针菇', '金针菇锡纸烤制，蒜蓉调味'],
      ['烤年糕', '年糕炭烤，外脆内软，刷甜酱'],
      ['烤面筋', '面筋串炭烤，孜然辣椒面，筋道入味'],
      ['烤鸡皮', '鸡皮烤至酥脆，油脂焦香'],
    ],
  },
  {
    type: '粤菜', merchants: ['粤味轩', '岭南食府', '广府家宴'],
    dishes: [
      ['白切鸡', '清远走地鸡，皮爽肉滑，配姜葱蘸料'],
      ['蜜汁叉烧', '梅花肉腌制烘烤，蜜汁焦香，肥瘦相间'],
      ['深井烧鹅', '黑鬃鹅明炉烧制，皮脆肉嫩，蘸酸梅酱'],
      ['虾饺', '水晶皮包整虾仁，晶莹剔透，鲜甜弹牙'],
      ['肠粉', '布拉肠粉，米香嫩滑，配豉油'],
      ['煲仔饭', '瓦煲生米现煮，腊味香气四溢，锅巴焦脆'],
      ['豉汁蒸排骨', '肋排豆豉蒸制，鲜嫩脱骨，蒜香浓郁'],
      ['白灼虾', '鲜虾白灼，蘸豉油，原汁原味鲜甜'],
      ['老火靓汤', '慢火煲数小时，汤清味浓，滋补养颜'],
      ['干炒牛河', '河粉大火快炒，锅气十足，牛肉嫩滑'],
      ['豉油鸡', '酱油浸鸡，色泽红亮，咸香入味'],
      ['菠萝包', '酥皮菠萝包，外酥内软，配黄油一绝'],
      ['杨枝甘露', '芒果西柚西米露，清甜解腻，港式甜品'],
      ['双皮奶', '水牛奶炖制，奶香浓郁，嫩滑细腻'],
    ],
  },
  {
    type: '湘菜', merchants: ['湘里人家', '辣不怕土菜馆', '洞庭湖湘菜'],
    dishes: [
      ['剁椒鱼头', '鳙鱼头铺满剁椒蒸制，鲜辣嫩滑，配面条一绝'],
      ['辣椒炒肉', '螺丝椒配五花肉，锅气十足，下饭神器'],
      ['小炒黄牛肉', '黄牛肉大火爆炒，鲜辣嫩滑'],
      ['口味虾', '小龙虾香辣入味，虾肉Q弹'],
      ['湘西腊肉', '柴火熏制腊肉，咸香浓郁，配蒜苗'],
      ['干锅肥肠', '肥肠干锅煸炒，麻辣鲜香，越嚼越香'],
      ['酸豆角肉末', '酸豆角配肉末，酸辣开胃'],
      ['擂辣椒皮蛋', '青椒皮蛋擂制，香辣爽口'],
      ['剁椒蒸芋头', '芋头粉糯，剁椒鲜辣提味'],
      ['香辣鸡爪', '鸡爪卤制后爆炒，软糯脱骨，麻辣够味'],
      ['农家小炒肉', '土猪肉配青椒，咸香下饭'],
      ['腊味合蒸', '腊肉腊肠蒸制，油脂交融，咸香扑鼻'],
    ],
  },
  {
    type: '日料', merchants: ['一禾寿司', '樱花居酒屋', '和风料理'],
    dishes: [
      ['三文鱼刺身', '挪威三文鱼厚切，油脂丰盈，入口即化'],
      ['寿司拼盘', '多种手握寿司拼盘，醋饭配鲜鱼，清爽鲜甜'],
      ['天妇罗', '大虾时蔬裹薄浆炸制，外酥里嫩，蘸天汁'],
      ['鳗鱼饭', '蒲烧鳗鱼铺米饭，酱汁浓郁，鳗鱼肥美'],
      ['豚骨拉面', '猪骨浓汤熬制，面条筋道，叉烧溏心蛋'],
      ['寿喜锅', '和牛时蔬寿喜烧，甜咸汤汁，蘸生鸡蛋'],
      ['章鱼小丸子', '外酥内软，章鱼粒弹牙，柴鱼片跳舞'],
      ['照烧鸡排饭', '鸡排照烧酱烤，甜咸焦香，配米饭'],
      ['味噌汤', '豆腐海带味噌汤，鲜香暖胃'],
      ['玉子烧', '鸡蛋卷层层煎制，松软微甜'],
      ['刺身拼盘', '多种生鱼片拼盘，配山葵酱油，鲜甜'],
      ['加州卷', '牛油果蟹柳卷，清爽不腻'],
    ],
  },
  {
    type: '西餐', merchants: ['菲力牛排馆', '意式小馆'],
    dishes: [
      ['安格斯牛排', '谷饲安格斯西冷，高温煎制，外焦里嫩锁肉汁'],
      ['意大利面', '番茄肉酱意面，酱汁浓郁，面条弹牙'],
      ['玛格丽特披萨', '番茄罗勒芝士，意式经典，饼底薄脆'],
      ['凯撒沙拉', '罗马生菜配凯撒酱，帕玛森芝士，清爽开胃'],
      ['奶油蘑菇汤', '口蘑奶油浓汤，丝滑浓郁'],
      ['汉堡', '安格斯牛肉饼，芝士生菜番茄，多汁厚实'],
      ['薯条', '粗切薯条现炸，外脆内绵'],
      ['提拉米苏', '马斯卡彭芝士手指饼，可可微苦，绵密丝滑'],
      ['香煎三文鱼', '三文鱼煎至金黄，配柠檬黄油汁'],
      ['烤鸡', '整鸡烤制，外皮酥脆，肉嫩多汁'],
      ['芝士焗饭', '米饭芝士焗烤，拉丝浓郁'],
      ['红酒烩牛肉', '牛肉红酒慢炖，软烂入味'],
    ],
  },
  {
    type: '甜品', merchants: ['甜心烘焙', '喜茶同款奶茶铺', '糖水铺子'],
    dishes: [
      ['珍珠奶茶', '黑糖珍珠Q弹，奶香茶香交融'],
      ['芝士奶盖茶', '咸香芝士奶盖，配茉莉绿茶底'],
      ['杨枝甘露', '芒果西柚西米，清甜浓郁'],
      ['双皮奶', '奶香浓郁，嫩滑细腻'],
      ['芒果班戟', '奶油芒果班戟，外皮软糯'],
      ['提拉米苏', '咖啡可可味，绵密丝滑'],
      ['草莓蛋糕', '奶油草莓蛋糕，酸甜清爽'],
      ['抹茶冰淇淋', '抹茶微苦回甘，清爽解腻'],
      ['烧仙草', '仙草冻配芋圆红豆，清凉降火'],
      ['芋圆糖水', '手工芋圆，Q弹软糯，红糖水打底'],
      ['马卡龙', '法式小圆饼，外脆内软，多种口味'],
      ['芝士蛋糕', '重乳酪蛋糕，绵密浓郁'],
    ],
  },
  {
    type: '面食', merchants: ['张记面馆分店', '兰州拉面馆', '山西刀削面'],
    dishes: [
      ['红烧牛肉面', '大块牛腱肉，慢炖8小时，浓郁骨汤打底'],
      ['兰州牛肉拉面', '现拉面条，清汤萝卜，牛肉片香菜'],
      ['刀削面', '面叶外滑内筋，配肉臊子，浇头浓郁'],
      ['油泼面', '宽面配辣椒面蒜末，热油泼香'],
      ['炸酱面', '五花肉黄酱炸制，拌面咸香'],
      ['鲜肉馄饨', '皮薄馅大，鲜汤打底，紫菜虾皮提鲜'],
      ['猪肉白菜饺子', '手工水饺，皮薄馅足，蘸醋蒜'],
      ['肉夹馍', '白吉馍夹卤肉，馍酥肉香'],
      ['凉皮', '筋道凉皮，配面筋黄瓜，酸辣开胃'],
      ['biangbiang面', '裤带面宽厚筋道，油泼辣子香'],
      ['葱油拌面', '葱油熬香，拌面油润鲜美'],
      ['雪菜肉丝面', '雪菜肉丝浇头，汤鲜味浓'],
    ],
  },
  {
    type: '快餐', merchants: ['快乐汉堡', '街角简餐', '元气便当'],
    dishes: [
      ['炸鸡腿', '外酥里嫩，汁水丰盈，多种蘸酱'],
      ['薯条', '现炸薯条，外脆内绵，蘸番茄酱'],
      ['鸡块', '黄金鸡块，酥脆多汁'],
      ['牛肉汉堡', '牛肉饼芝士生菜，厚实满足'],
      ['蛋炒饭', '粒粒分明，锅气十足，火腿鸡蛋'],
      ['宫保鸡丁盖饭', '鸡丁花生盖饭，甜酸微辣'],
      ['红烧肉盖饭', '红烧肉软糯，汤汁拌饭一绝'],
      ['麻辣烫', '自选菜品，麻辣汤底，热气腾腾'],
      ['关东煮', '萝卜鱼丸豆腐，汤鲜味美'],
      ['鸡肉卷', '墨西哥卷饼包鸡肉，酱汁浓郁'],
      ['热狗', '面包夹香肠，芥末番茄酱'],
      ['烤肠', '炭烤香肠，皮脆肉香'],
    ],
  },
  {
    type: '海鲜', merchants: ['海鲜大咖', '渔家小馆'],
    dishes: [
      ['清蒸鲈鱼', '鲜活鲈鱼清蒸，葱姜豉油，肉质鲜嫩'],
      ['白灼虾', '鲜虾白灼，原汁原味鲜甜'],
      ['蒜蓉粉丝蒸扇贝', '扇贝铺蒜蓉粉丝，鲜香浓郁'],
      ['辣炒花蛤', '花蛤爆炒，鲜辣入味，汤汁拌饭'],
      ['避风塘炒蟹', '梭子蟹避风塘做法，蒜香酥脆'],
      ['椒盐皮皮虾', '皮皮虾椒盐炸制，外壳酥脆'],
      ['清蒸大闸蟹', '大闸蟹清蒸，蟹黄丰腴'],
      ['海鲜粥', '鲜虾蟹肉熬粥，鲜甜绵滑'],
      ['香煎带鱼', '带鱼煎至两面金黄，外酥里嫩'],
      ['海胆蒸蛋', '海胆鸡蛋蒸制，鲜滑细腻'],
    ],
  },
  {
    type: '轻食', merchants: ['轻食主义', '元气沙拉'],
    dishes: [
      ['鸡胸肉沙拉', '低温慢煮鸡胸，蔬菜基底，低卡高蛋白'],
      ['藜麦碗', '藜麦鸡胸牛油果，营养均衡'],
      ['牛油果吐司', '全麦吐司配牛油果，健康饱腹'],
      ['鲜榨果汁', '新鲜水果现榨，无添加'],
      ['燕麦酸奶杯', '燕麦酸奶水果分层，低卡美味'],
      ['金枪鱼沙拉', '金枪鱼蔬菜沙拉，清爽低脂'],
      ['蔬菜卷', '全麦饼卷时蔬，健康轻负担'],
      ['南瓜浓汤', '南瓜浓汤，绵密香甜，无奶油'],
      ['水果拼盘', '当季水果拼盘，新鲜清爽'],
      ['希腊酸奶', '浓稠希腊酸奶，配蜂蜜坚果'],
    ],
  },
  {
    type: '江浙菜', merchants: ['江南忆', '杭帮菜馆'],
    dishes: [
      ['红烧肉', '五花肉慢炖，肥而不腻，甜咸适口'],
      ['糖醋排骨', '镇江香醋配冰糖，酸甜适中'],
      ['西湖醋鱼', '草鱼糖醋烹制，酸甜鲜嫩，杭帮名菜'],
      ['龙井虾仁', '龙井茶香虾仁，清鲜淡雅'],
      ['东坡肉', '黄酒慢炖，酥烂入味，肥而不腻'],
      ['叫花鸡', '荷叶包裹烤制，鸡肉鲜嫩，香气扑鼻'],
      ['清炒时蔬', '当季蔬菜蒜蓉清炒，清爽'],
      ['腌笃鲜', '咸肉鲜肉春笋炖汤，咸鲜浓郁'],
      ['油焖笋', '春笋油焖，咸甜入味，脆嫩'],
      ['桂花糖藕', '糯米糖藕，桂花蜜香，软糯香甜'],
    ],
  },
  {
    type: '西北菜', merchants: ['西北人家', '大漠食府'],
    dishes: [
      ['羊肉泡馍', '掰馍配羊肉汤，鲜香浓郁，暖胃饱腹'],
      ['肉夹馍', '白吉馍夹腊汁肉，馍酥肉烂'],
      ['凉皮', '陕西凉皮，酸辣筋道'],
      ['大盘鸡', '鸡肉土豆宽面，香辣浓郁，分量十足'],
      ['手抓羊肉', '清水煮羊肉，蘸椒盐，原汁原味'],
      ['biangbiang面', '裤带面油泼辣子，筋道香辣'],
      ['烤羊排', '羊排炭烤，孜然辣椒，外焦里嫩'],
      ['臊子面', '酸辣臊子汤面，开胃筋道'],
      ['羊肉串', '草原羊肉炭烤，孜然香浓'],
      ['炒面片', '面片配牛羊肉炒制，筋道入味'],
    ],
  },
  {
    type: '韩餐', merchants: ['首尔烤肉', '韩屋料理'],
    dishes: [
      ['石锅拌饭', '石锅米饭配时蔬鸡蛋，锅巴焦香'],
      ['部队火锅', '午餐肉香肠拉面一锅煮，芝士浓郁'],
      ['韩式炸鸡', '炸鸡裹甜辣酱，外酥里嫩'],
      ['辣白菜', '韩式泡菜，酸辣爽脆'],
      ['参鸡汤', '童子鸡糯米人参炖汤，滋补鲜美'],
      ['紫菜包饭', '海苔包饭，清爽便当'],
      ['烤五花肉', '五花肉铁板烤制，包生菜蒜片'],
      ['大酱汤', '韩式大酱汤，豆腐时蔬，咸香浓郁'],
      ['韩式冷面', '荞麦冷面，冰爽酸甜，夏天一绝'],
      ['年糕火锅', '韩式辣年糕，软糯入味'],
    ],
  },
];

// ============ 生成器 ============
const lines = [];
lines.push('-- ==========================================');
lines.push('-- 吉味生活 · 大规模 mock 数据（自动生成）');
lines.push('-- ==========================================');
lines.push('');

let merchantId = 100;
let categoryId = 200;
let dishId = 1000;
const userIdStart = 100;
const userIdEnd = 400; // 300 个用户
const orderIdStart = 1000;
const orderCount = 4000;
const noteIdStart = 100;
const noteCount = 220;
const reviewIdStart = 100;
const reviewCount = 400;
const commentIdStart = 5000; // 评论ID（避开种子数据 1-9）

// 商户 + 分类 + 菜品
const merchantRows = [];
const categoryRows = [];
const dishRows = [];
const dishRefs = []; // {id, merchantId, categoryId, name}

for (const cu of cuisines) {
  for (let m = 0; m < cu.merchants.length; m++) {
    const id = merchantId++;
    const name = cu.merchants[m];
    const district = pick(['上城区', '拱墅区', '西湖区', '滨江区', '余杭区', '下城区']);
    const road = pick(['中山路', '文三路', '莫干山路', '江南大道', '古墩路', '文一西路', '武林路', '延安路']);
    const address = `杭州市${district}${road}${randInt(1, 999)}号`;
    const lng = rand(120.05, 120.28).toFixed(6);
    const lat = rand(30.18, 30.35).toFixed(6);
    const phone = '138' + String(randInt(10000000, 99999999));
    const hours = pick(['06:00-21:00', '10:00-22:00', '11:00-23:00', '17:00-02:00', '09:00-21:30']);
    merchantRows.push(`(${id},${q(name)},${q('https://api.dicebear.com/7.x/icons/svg?seed=m' + id)},${q(cu.type + '招牌餐厅，食材新鲜，口味地道，深受食客喜爱')},${q(address)},${lng},${lat},${q(phone)},${q(hours)},${randInt(3000, 5000)},1,${(m % 4) + 2},NOW(),NOW())`);

    // 每个商户 3-5 个分类
    const catNames = pick([['招牌菜', '经典菜', '主食', '汤品'], ['热菜', '凉菜', '主食'], ['招牌', '特色', '饮品', '小食'], ['人气', '时令', '主食']]);
    const catCount = randInt(3, Math.min(5, catNames.length));
    const catIds = [];
    for (let c = 0; c < catCount; c++) {
      const cid = categoryId++;
      catIds.push(cid);
      categoryRows.push(`(${cid},${id},1,${q(catNames[c])},${c + 1},1,NOW(),NOW())`);
    }

    // 每个商户选 15-20 个菜品
    const shuffled = [...cu.dishes].sort(() => Math.random() - 0.5);
    const dishCount = Math.min(randInt(15, 20), shuffled.length);
    for (let d = 0; d < dishCount; d++) {
      const did = dishId++;
      const [dname, ddesc] = shuffled[d];
      const cid = pick(catIds);
      const dp = price(6, 88);
      const sales = randInt(100, 5000);
      dishRows.push(`(${did},${id},${cid},${q(dname)},${q('https://api.dicebear.com/7.x/icons/svg?seed=d' + did)},${q(ddesc)},${dp},1,${sales},NOW(),NOW(),${(m % 4) + 2},${(m % 4) + 2})`);
      dishRefs.push({ id: did, merchantId: id, name: dname, price: dp });
    }
  }
}

// 用户
const userRows = [];
const surnames = ['陈', '李', '王', '张', '刘', '赵', '孙', '周', '吴', '郑', '黄', '林', '徐', '郭', '马', '朱'];
const foodTags = ['火锅', '川菜', '粤菜', '湘菜', '烧烤', '日料', '西餐', '甜品', '奶茶', '面食', '海鲜', '轻食', '江浙菜', '西北菜', '韩餐', '快餐', '深夜食堂', '减脂餐', '聚餐', '约会'];
const levels = [1, 2, 3, 4, 5];
for (let i = userIdStart; i <= userIdEnd; i++) {
  const nickname = pick(surnames) + pick(['先生', '女士', '同学', '吃货', '老饕', '小仙女', '小哥哥']) + i;
  const tags = [];
  const tagCount = randInt(2, 4);
  const shuffledTags = [...foodTags].sort(() => Math.random() - 0.5);
  for (let t = 0; t < tagCount; t++) tags.push(shuffledTags[t]);
  const growth = randInt(0, 5000);
  userRows.push(`(${i},${q('mock_openid_' + String(i).padStart(4, '0'))},${q(nickname)},${q('https://api.dicebear.com/7.x/avataaars/svg?seed=u' + i)},${q('139' + String(randInt(10000000, 99999999)))},${pick([1, 2])},${growth},${pick(levels)},${q(JSON.stringify(tags))},1,NOW(),NOW())`);
}

// 订单 + 明细
const orderRows = [];
const detailRows = [];
const payMethods = [1, 2];
const orderStatuses = [1, 2, 3, 4, 5, 6];
for (let o = 0; o < orderCount; o++) {
  const oid = orderIdStart + o;
  const userId = randInt(userIdStart, userIdEnd);
  const ref = pick(dishRefs);
  const merchantId = ref.merchantId;
  const amount = Number(ref.price) + rand(0, 40);
  const discount = (rand(0, 15)).toFixed(2);
  const deliveryFee = (rand(0, 5)).toFixed(2);
  const payAmount = (amount - Number(discount) + Number(deliveryFee)).toFixed(2);
  const status = pick(orderStatuses);
  const payStatus = status >= 3 ? 1 : pick([0, 1]);
  const number = 'SV' + String(randInt(10000000, 99999999));
  const d = dt();
  orderRows.push(`(${oid},${q(number)},${userId},${merchantId},${userId},${q('杭州市' + pick(['上城区', '西湖区', '滨江区']) + pick(['中山路', '文三路', '江南大道']) + randInt(1, 999) + '号')},${amount.toFixed(2)},${discount},${deliveryFee},${payAmount},${pick(payMethods)},${payStatus},${status},${status >= 3 ? q('wx_txn_mock_' + oid) : 'NULL'},${q('')},0,${status >= 3 ? q(d) : 'NULL'},${q(d)},NOW())`);

  // 每个订单 1-3 个明细
  const detailCount = randInt(1, 3);
  for (let dd = 0; dd < detailCount; dd++) {
    const r2 = pick(dishRefs);
    detailRows.push(`(${oid},${q(r2.name)},${q('https://api.dicebear.com/7.x/icons/svg?seed=dd' + r2.id)},${q('')},${r2.price},${randInt(1, 2)})`);
  }
}

// 笔记
const noteRows = [];
const noteTitles = [
  '杭州必吃！{name}深度测评', '{name}打卡全攻略', '周末探店｜{name}值不值得去', '{name}隐藏菜单大公开',
  '人均50吃到扶墙出｜{name}', '深夜放毒！{name}真的绝了', '减脂期也能吃｜{name}点单攻略', '{name}聚餐体验分享',
];
const noteBodies = [
  '作为一个资深吃货，今天终于来打卡{name}。{dish}真的惊艳到我了！食材非常新鲜，分量也足，重点是价格很亲民。强烈推荐给所有喜欢{cusine}的朋友，绝对不会踩雷。建议错峰去，饭点人会比较多。',
  '周末和朋友一起来{name}，点了招牌{dish}和几道小菜。味道没得说，{dish}是全场最佳，火候掌握得恰到好处。环境也很干净，服务态度好。下次还会再来，已经安利给同事了。',
  '冲着{dish}来的，果然没让我失望。{cusine}爱好者必冲！性价比在杭州真的很能打，人均不到100。就是周末人太多了，建议工作日中午来。',
  '减脂期外食首选{name}，{dish}完全不油腻，吃完没有负罪感。食材新鲜，做法健康，强烈推荐给健身的朋友们。',
];
const locations = ['杭州市上城区', '杭州市西湖区', '杭州市滨江区', '杭州市拱墅区', '杭州市余杭区'];

// 评论内容池（一级 + 二级回复）
const commentContents = [
  '看饿了！这周就去打卡', '收藏了，周末安排上', '这家真的好吃，我上次去人超多', '博主拍得也太诱人了吧',
  '求地址，想带爸妈去', '跟着博主不踩雷', '吃过的表示确实不错', '性价比看起来很高诶', '下次去杭州就去这家',
  '这个{cusine}看着太对味了', '已经安利给全宿舍了', '工作日中午去人会不会少点', '看起来比上次我去那家强',
  '价格这么实惠的吗？', '馋哭了，深夜看到这个太残忍', '请问人均大概多少呀', '带娃去合适吗？', '这家店好停车吗',
];
const commentReplies = [
  '同问同问！蹲一个回答', '哈哈哈我也是这么想的', '确实，实名赞同', '姐妹冲就完事了', '安排！一起约起来',
  '别犹豫了，直接去', '信我，去了不亏', '这波种草成功', '我作证，真的不错', '哈哈哈馋死我了',
];

// 评论生成：每篇笔记 2~8 条一级评论，其中约一半带 1~3 条二级回复
const commentRows = [];
let commentId = commentIdStart;
for (let n = 0; n < noteCount; n++) {
  const nid = noteIdStart + n;
  const userId = randInt(userIdStart, userIdEnd);
  const ref = pick(dishRefs);
  const merchantId = ref.merchantId;
  const name = ref.name;
  const title = pick(noteTitles).replaceAll('{name}', name);
  const body = pick(noteBodies).replaceAll('{name}', name).replaceAll('{dish}', ref.name).replaceAll('{cusine}', pick(['川菜', '粤菜', '湘菜', '烧烤', '日料', '火锅', '江浙菜', '西北菜']));
  const topicTags = JSON.stringify([pick(['美食探店', '杭州吃喝', '深夜食堂', '周末聚餐', '减脂餐', '性价比']), pick(['必吃榜', '网红店', '老字号', '打卡']), pick(['面食地图', '烧烤地图', '火锅地图', '甜品地图'])]);
  const cusine = pick(['川菜', '粤菜', '湘菜', '烧烤', '日料', '火锅', '江浙菜', '西北菜']);

  // 为这篇笔记生成真实评论，comment_count 取实际条数
  const topCount = randInt(2, 8);
  const topCommentIds = [];
  for (let c = 0; c < topCount; c++) {
    const cid = commentId++;
    topCommentIds.push(cid);
    const cu = randInt(userIdStart, userIdEnd);
    const content = pick(commentContents).replace('{cusine}', cusine);
    commentRows.push(`(${cid},${nid},${cu},NULL,NULL,${q(content)},${randInt(0, 50)},${q(dt())})`);
  }
  // 二级回复：约一半一级评论带 1~3 条回复
  let replyCount = 0;
  for (const pid of topCommentIds) {
    if (Math.random() < 0.5) {
      const rn = randInt(1, 3);
      for (let r = 0; r < rn; r++) {
        const rid = commentId++;
        replyCount++;
        const ru = randInt(userIdStart, userIdEnd);
        commentRows.push(`(${rid},${nid},${ru},${pid},${pid},${q(pick(commentReplies))},${randInt(0, 20)},${q(dt())})`);
      }
    }
  }
  const totalComments = topCount + replyCount;
  noteRows.push(`(${nid},${userId},${q(title)},${q(body)},${merchantId},${q(topicTags)},${q(pick(locations))},${randInt(0, 800)},${totalComments},${randInt(0, 300)},${randInt(100, 8000)},1,${pick([0, 1])},${q(dt())},NOW())`);
}

// 评价
const reviewRows = [];
const reviewContents = [
  '味道非常好，{dish}很惊艳，下次还会点', '分量足，口味地道，推荐给同事了', '配送快，包装好，{dish}味道不错',
  '一般般，{dish}偏咸，希望改进', '超级好吃！{dish}是招牌，必点', '性价比高，{dish}很划算',
  '口感不错，就是等餐久了点', '回头客了，{dish}一直稳定好吃',
];
const reviewTags = [['分量足', '味道好'], ['超值', '推荐'], ['必吃榜'], ['一般'], ['偏咸'], ['回头客']];
for (let r = 0; r < reviewCount; r++) {
  const rid = reviewIdStart + r;
  const userId = randInt(userIdStart, userIdEnd);
  const ref = pick(dishRefs);
  const rating = randInt(3, 5);
  const content = pick(reviewContents).replace('{dish}', ref.name);
  reviewRows.push(`(${rid},${userId},${randInt(orderIdStart, orderIdStart + orderCount - 1)},${ref.id},${rating},${q(content)},${q(JSON.stringify(pick(reviewTags)))},0,1,${randInt(0, 100)},${q(dt())})`);
}

// ============ 输出 ============
lines.push('-- 商户');
lines.push('INSERT INTO jiwei_merchant.merchant_info (id, name, logo, description, address, longitude, latitude, phone, business_hours, delivery_range, status, emp_id, create_time, update_time) VALUES');
lines.push(merchantRows.join(',\n') + ';');
lines.push('');

lines.push('-- 分类');
lines.push('INSERT INTO jiwei_merchant.category (id, merchant_id, type, name, sort, status, create_time, update_time) VALUES');
lines.push(categoryRows.join(',\n') + ';');
lines.push('');

lines.push('-- 菜品');
lines.push('INSERT INTO jiwei_merchant.dish (id, merchant_id, category_id, name, image, description, price, status, sales, create_time, update_time, create_user, update_user) VALUES');
lines.push(dishRows.join(',\n') + ';');
lines.push('');

lines.push('-- 用户');
lines.push('INSERT INTO jiwei_user.user (id, openid, nickname, avatar, phone, sex, growth_value, level, preference_tags, status, create_time, update_time) VALUES');
lines.push(userRows.join(',\n') + ';');
lines.push('');

lines.push('-- 订单');
lines.push('INSERT INTO jiwei_trade.orders (id, number, user_id, merchant_id, address_book_id, address_detail, amount, discount_amount, delivery_fee, pay_amount, pay_method, pay_status, status, transaction_id, remark, is_seckill, pay_time, create_time, update_time) VALUES');
lines.push(orderRows.join(',\n') + ';');
lines.push('');

lines.push('-- 订单明细');
lines.push('INSERT INTO jiwei_trade.order_detail (order_id, name, image, dish_flavor, amount, number) VALUES');
lines.push(detailRows.join(',\n') + ';');
lines.push('');

lines.push('-- 笔记');
lines.push('INSERT INTO jiwei_social.note (id, user_id, title, content, merchant_id, topic_tags, location, like_count, comment_count, collect_count, view_count, audit_status, is_top, create_time, update_time) VALUES');
lines.push(noteRows.join(',\n') + ';');
lines.push('');

lines.push('-- 评价');
lines.push('INSERT INTO jiwei_social.review (id, user_id, order_id, dish_id, rating, content, tags, is_ai_assisted, audit_status, like_count, create_time) VALUES');
lines.push(reviewRows.join(',\n') + ';');
lines.push('');

lines.push('-- 评论');
lines.push('INSERT INTO jiwei_social.comment (id, note_id, user_id, parent_id, reply_to_user_id, content, like_count, create_time) VALUES');
lines.push(commentRows.join(',\n') + ';');
lines.push('');

const out = join(__dirname, '98_mock_data.sql');
writeFileSync(out, lines.join('\n'), 'utf-8');
console.log(`生成完成: ${out}`);
console.log(`商户 ${merchantRows.length}, 分类 ${categoryRows.length}, 菜品 ${dishRows.length}, 用户 ${userRows.length}, 订单 ${orderRows.length}, 明细 ${detailRows.length}, 笔记 ${noteRows.length}, 评价 ${reviewRows.length}, 评论 ${commentRows.length}`);
