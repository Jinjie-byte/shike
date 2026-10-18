/* ============================================================
   食刻用户端 H5 —— 应用逻辑
   ------------------------------------------------------------
   技术选型说明（为什么这么写）：
     · Vue 3 用本地文件引入，不依赖 CDN —— 部署到服务器也不会因为
       外网访问不到而白屏
     · 不用构建工具（webpack/vite）—— 改完刷新就生效，
       丢进 nginx 的目录就能跑，演示部署零成本
     · 页面切换用简单的状态变量，不引 vue-router —— 只有 3 个页面，
       没必要为此多引一个库
   ============================================================ */

const { createApp, ref, computed, onMounted, nextTick } = Vue;

/* ==================== 接口层 ==================== */

const TOKEN_KEY = 'shike_h5_token';

function getToken() { return localStorage.getItem(TOKEN_KEY) || ''; }
function setToken(t) { localStorage.setItem(TOKEN_KEY, t); }

/**
 * 统一请求封装 —— 相当于 Java 里的 RestTemplate / 前端的 axios
 *
 * 注意这里都是【相对路径】（/user/... 而不是 http://127.0.0.1:8080/user/...）：
 * 请求先到 nginx，由 nginx 转给后端。好处是没有跨域问题，
 * 而且后端换机器/换端口时前端一行都不用改。
 */
async function http(url, { method = 'GET', body, auth = true } = {}) {
  const headers = {};
  if (body) headers['Content-Type'] = 'application/json';
  if (auth && getToken()) headers['authentication'] = getToken();   // 用户端 token 头

  const res = await fetch(url, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) throw new Error('HTTP ' + res.status);

  const json = await res.json();
  if (json.code !== 1) throw new Error(json.msg || '请求失败');
  return json.data;
}

/* ==================== 应用 ==================== */

createApp({
  setup() {
    /* ---------- 全局状态 ---------- */
    const page = ref('home');
    const title = ref('食刻外卖');
    const toast = ref('');
    const loading = ref(false);

    /* ---------- 首页 ---------- */
    const shopStatus = ref(1);
    const typeListData = ref([]);     // 分类
    const typeIndex = ref(0);         // 当前分类
    const dishListItems = ref([]);    // 当前分类下的菜品
    const cart = ref([]);             // 购物车

    /* ---------- 我的 ---------- */
    const userInfo = ref({});
    const historyList = ref([]);

    /* ---------- AI 点单 ---------- */
    // 会话 id = 多轮记忆的"主键"。
    // 存 localStorage 是为了刷新页面后还能接着上一段对话；
    // 点「新对话」就换一个新的，等于清空记忆。
    const AI_SESSION_KEY = 'shike_ai_session';
    function newSessionId() {
      return 'sess-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 8);
    }
    const aiSessionId = ref(localStorage.getItem(AI_SESSION_KEY) || newSessionId());
    localStorage.setItem(AI_SESSION_KEY, aiSessionId.value);

    const aiMessages = ref([
      { role: 'ai', text: '你好，我是食刻的 AI 点单助手。\n可以直接告诉我口味和预算，比如「想吃辣的，30块以内」。', welcome: true },
    ]);
    const aiInput = ref('');
    const aiLoading = ref(false);

    /* ---------- 计算属性 ---------- */
    const cartCount = computed(() =>
      cart.value.reduce((s, c) => s + (c.number || 0), 0));

    // ★ amount 是【单价】，不是小计 —— 必须 × number，否则点 2 份只显示 1 份的钱
    const cartTotal = computed(() =>
      cart.value.reduce((s, c) => s + Number(c.amount || 0) * (c.number || 1), 0));

    function money(v) { return Number(v || 0).toFixed(2); }

    /**
     * 菜品图加载失败时的兜底。
     * 原项目的图片挂在教程作者的 OSS 桶上，现在全部返回 403，
     * 用【分类对应的 emoji】顶上 —— 比"菜名首字色块"更像一张图。
     */
    function onDishImgError(dish, event) {
      const img = event.target;
      const box = img.parentNode;
      if (!box || box.classList.contains('img-fallback')) return;
      box.classList.add('img-fallback', 'tone-' + catMeta.value.tone);
      box.setAttribute('data-initial', catMeta.value.icon);
      img.style.display = 'none';
    }

    // 分类 → 占位图标 + 色调
    const CAT_ICONS = [
      { keys: ['鱼', '川', '湘', '水煮', '烤'], icon: '🐟', tone: 'warm' },
      { keys: ['蛙'], icon: '🐸', tone: 'green' },
      { keys: ['蔬', '菜', '素'], icon: '🥬', tone: 'green' },
      { keys: ['主食', '饭', '面', '饼', '包'], icon: '🍚', tone: 'amber' },
      { keys: ['酒', '饮', '水', '茶'], icon: '🥤', tone: 'blue' },
      { keys: ['汤'], icon: '🍲', tone: 'orange' },
    ];
    const catMeta = computed(() => {
      const name = (typeListData.value[typeIndex.value] || {}).name || '';
      return CAT_ICONS.find(c => c.keys.some(k => name.includes(k)))
        || { icon: '🍽️', tone: 'warm' };
    });

    function showToast(msg, ms = 1800) {
      toast.value = msg;
      setTimeout(() => { toast.value = ''; }, ms);
    }

    /* ---------- 登录 ---------- */
    // 后端开了 mock 开关（shike.wechat.enable-mock=true），所以随便传个 code 就能换到 token。
    // 上线前这个开关要关掉，那时这里必须换成真实的微信登录流程。
    async function initUser() {
      if (getToken()) return;
      const data = await http('/user/user/login', {
        method: 'POST', body: { code: 'h5-demo' }, auth: false,
      });
      setToken(data.token);
      userInfo.value = data;
    }

    /* ---------- 首页数据 ---------- */
    async function loadShopStatus() {
      try { shopStatus.value = await http('/user/shop/status', { auth: false }); }
      catch (e) { /* 拿不到就按营业中显示 */ }
    }

    async function loadCategories() {
      typeListData.value = (await http('/user/category/list?type=1')) || [];
      if (typeListData.value.length) await switchType(0);
    }

    async function switchType(i) {
      typeIndex.value = i;
      const c = typeListData.value[i];
      if (!c) return;
      loading.value = true;
      try {
        const list = (await http('/user/dish/list?categoryId=' + c.id)) || [];
        dishListItems.value = list.map(d => ({ ...d, dishNumber: 0 }));
        applyCartToDishes();
      } catch (e) {
        showToast(e.message);
      } finally {
        loading.value = false;
      }
    }

    /* ---------- 购物车 ---------- */
    const showCart = ref(false);

    async function loadCart() {
      try {
        cart.value = (await http('/user/shoppingCart/list')) || [];
        applyCartToDishes();
      } catch (e) { /* 未登录等，忽略 */ }
    }

    // 把购物车数量映射回菜品列表（界面上那个 +N 的数字）
    // 同一道菜可能因为口味不同分成多行，所以要累加
    function applyCartToDishes() {
      const map = {};
      cart.value.forEach(c => {
        if (c.dishId) map[c.dishId] = (map[c.dishId] || 0) + (c.number || 0);
      });
      dishListItems.value.forEach(d => { d.dishNumber = map[d.id] || 0; });
    }

    function openCart() {
      if (!cart.value.length) { showToast('购物车还是空的'); return; }
      showCart.value = true;
    }
    function closeCart() { showCart.value = false; }

    async function clearCart() {
      try {
        await http('/user/shoppingCart/clean', { method: 'DELETE' });
        await loadCart();
        showCart.value = false;
        showToast('购物车已清空');
      } catch (e) { showToast(e.message); }
    }

    /** 点 + ：有口味的菜先弹详情让用户选口味，没有就直接加 */
    async function addDish(d) {
      if (d.flavors && d.flavors.length) { openDishDetail(d); return; }
      try {
        await http('/user/shoppingCart/add', { method: 'POST', body: { dishId: d.id } });
        await loadCart();
      } catch (e) { showToast(e.message); }
    }

    /** 点 − ：有口味时要把对应那行的口味一起传，否则后端定位不到 */
    async function subDish(d) {
      const row = cart.value.find(c => c.dishId === d.id);
      try {
        await http('/user/shoppingCart/sub', {
          method: 'POST',
          body: { dishId: d.id, dishFlavor: row ? row.dishFlavor : undefined },
        });
        await loadCart();
      } catch (e) { showToast(e.message); }
    }

    /* ---------- 购物车弹层里的加减（按购物车行操作） ---------- */
    async function cartAdd(c) {
      try {
        await http('/user/shoppingCart/add', {
          method: 'POST',
          body: { dishId: c.dishId, setmealId: c.setmealId, dishFlavor: c.dishFlavor },
        });
        await loadCart();
      } catch (e) { showToast(e.message); }
    }
    async function cartSub(c) {
      try {
        await http('/user/shoppingCart/sub', {
          method: 'POST',
          body: { dishId: c.dishId, setmealId: c.setmealId, dishFlavor: c.dishFlavor },
        });
        await loadCart();
        if (!cart.value.length) showCart.value = false;
      } catch (e) { showToast(e.message); }
    }

    /* ---------- 菜品详情 / 选口味 ---------- */
    const showDish = ref(false);
    const dishDetail = ref(null);
    const flavorSel = ref({});

    /** dish_flavor.value 是 JSON 字符串，形如 ["不辣","微辣","中辣"] */
    function parseFlavors(v) {
      try {
        const arr = JSON.parse(v);
        return Array.isArray(arr) ? arr : [];
      } catch (e) { return []; }
    }

    function openDishDetail(d) {
      dishDetail.value = d;
      const sel = {};
      (d.flavors || []).forEach(f => {
        const vals = parseFlavors(f.value);
        if (vals.length) sel[f.name] = vals[0];   // 默认选第一个
      });
      flavorSel.value = sel;
      showDish.value = true;
    }
    function closeDish() { showDish.value = false; }
    function pickFlavor(name, val) {
      flavorSel.value = { ...flavorSel.value, [name]: val };
    }
    // 菜品图 403（教程 OSS 桶已失效）时，连整个图片区一起藏掉，别留一块空白
    function onPopImgError(e) {
      const box = e.target.parentNode;
      if (box) box.style.display = 'none';
      else e.target.style.display = 'none';
    }

    async function addWithFlavor() {
      const d = dishDetail.value;
      if (!d) return;
      const flavor = Object.values(flavorSel.value).filter(Boolean).join(',');
      try {
        await http('/user/shoppingCart/add', {
          method: 'POST',
          body: { dishId: d.id, dishFlavor: flavor || undefined },
        });
        await loadCart();
        showDish.value = false;
        showToast('已加入购物车');
      } catch (e) { showToast(e.message); }
    }

    function checkoutFromCart() {
      showCart.value = false;
      goOrder();
    }

    /* ---------- 结算 ---------- */
    const address = ref(null);
    const addrForm = ref({ consignee: '', phone: '', detail: '', label: '家' });
    const remark = ref('');
    const submitting = ref(false);
    const ADDR_LABELS = ['家', '公司', '学校'];

    async function loadAddress() {
      try { address.value = await http('/user/addressBook/default') || null; }
      catch (e) { address.value = null; }
    }

    async function goOrder() {
      if (cartCount.value === 0) { showToast('请先选择菜品'); return; }
      await loadAddress();
      go('order');
    }

    function fmtDateTime(d) {
      const p = n => String(n).padStart(2, '0');
      return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} `
        + `${p(d.getHours())}:${p(d.getMinutes())}:00`;
    }

    async function submitOrder() {
      if (submitting.value) return;
      if (!cart.value.length) { showToast('购物车是空的'); return; }
      if (!address.value) { showToast('请先添加收货地址'); return; }

      submitting.value = true;
      try {
        const eta = fmtDateTime(new Date(Date.now() + 30 * 60 * 1000));

        // ★ amount 传了也没用 —— 后端改造 1 会按购物车重新算（价格篡改漏洞已修）
        const order = await http('/user/order/submit', {
          method: 'POST',
          body: {
            addressBookId: address.value.id,
            payMethod: 1,
            remark: remark.value,
            estimatedDeliveryTime: eta,
            deliveryStatus: 1,
            tablewareNumber: 1,
            tablewareStatus: 1,
            packAmount: 0,
            amount: cartTotal.value,
          },
        });

        // 支付是模拟的（后端 payment() 里微信调用已被注释），这里直接调
        await http('/user/order/payment', {
          method: 'PUT',
          body: { orderNumber: order.orderNumber, payMethod: 1 },
        });

        lastOrder.value = { id: order.id, orderNumber: order.orderNumber, eta: eta.slice(11, 16) };
        remark.value = '';
        await loadCart();
        go('success');
      } catch (e) {
        showToast(e.message, 2600);
      } finally {
        submitting.value = false;
      }
    }

    /* ---------- 地址管理 ---------- */
    const addressList = ref([]);
    const editingId = ref(null);

    async function loadAddressList() {
      try { addressList.value = await http('/user/addressBook/list') || []; }
      catch (e) { addressList.value = []; }
    }

    async function goAddress() {
      await loadAddressList();
      go('address');
    }

    function newAddress() {
      editingId.value = null;
      addrForm.value = { consignee: '', phone: '', detail: '', label: '家' };
      go('addrEdit');
    }

    function editAddress(a) {
      editingId.value = a.id;
      addrForm.value = {
        consignee: a.consignee, phone: a.phone,
        detail: a.detail, label: a.label || '家',
      };
      go('addrEdit');
    }

    function validateAddr() {
      const f = addrForm.value;
      if (!f.consignee || !f.phone || !f.detail) { showToast('联系人、手机号、地址都要填'); return false; }
      if (!/^1\d{10}$/.test(f.phone)) { showToast('手机号格式不对'); return false; }
      return true;
    }

    async function saveAddrForm() {
      if (!validateAddr()) return;
      const f = addrForm.value;
      try {
        if (editingId.value) {
          await http('/user/addressBook', { method: 'PUT', body: { ...f, id: editingId.value } });
        } else {
          await http('/user/addressBook', { method: 'POST', body: { ...f, sex: '1' } });
        }
        await loadAddressList();
        await loadAddress();
        go('address');
        showToast('已保存');
      } catch (e) { showToast(e.message); }
    }

    async function removeAddress(a) {
      try {
        await http('/user/addressBook?id=' + a.id, { method: 'DELETE' });
        await loadAddressList();
        await loadAddress();
        showToast('已删除');
      } catch (e) { showToast(e.message); }
    }

    async function removeEditing() {
      if (editingId.value) await removeAddress({ id: editingId.value });
      go('address');
    }

    async function setDefaultAddr(a) {
      try {
        await http('/user/addressBook/default', { method: 'PUT', body: { id: a.id } });
        await loadAddressList();
        await loadAddress();
        showToast('已设为默认');
      } catch (e) { showToast(e.message); }
    }

    /** 在地址列表里点一条 = 选它下单 */
    function useAddress(a) {
      address.value = a;
      go('order');
    }

    /* ---------- 订单详情 / 成功页 ---------- */
    const orderDetail = ref(null);
    const lastOrder = ref(null);
    const STATUS_TEXT = { 1: '待付款', 2: '待接单', 3: '已接单', 4: '派送中', 5: '已完成', 6: '已取消' };
    function statusText(s) { return STATUS_TEXT[s] || '未知'; }

    async function openOrderDetail(o) {
      try {
        orderDetail.value = await http('/user/order/orderDetail/' + o.id);
        go('orderDetail');
      } catch (e) { showToast(e.message); }
    }

    async function reloadDetail() {
      orderDetail.value = await http('/user/order/orderDetail/' + orderDetail.value.id);
    }

    async function paidCurrent() {
      try {
        await http('/user/order/payment', {
          method: 'PUT',
          body: { orderNumber: orderDetail.value.number, payMethod: 1 },
        });
        await reloadDetail();
        showToast('支付成功');
      } catch (e) { showToast(e.message); }
    }

    async function cancelCurrent() {
      try {
        await http('/user/order/cancel/' + orderDetail.value.id, { method: 'PUT' });
        await reloadDetail();
        showToast('订单已取消');
      } catch (e) { showToast(e.message); }
    }

    async function remindCurrent() {
      try {
        await http('/user/order/reminder/' + orderDetail.value.id);
        showToast('已催单');
      } catch (e) { showToast(e.message); }
    }

    async function againCurrent() {
      try {
        await http('/user/order/repetition/' + orderDetail.value.id, { method: 'POST' });
        await loadCart();
        showToast('已加入购物车');
        go('home');
      } catch (e) { showToast(e.message); }
    }

    function viewLastOrder() {
      if (lastOrder.value && lastOrder.value.id) openOrderDetail({ id: lastOrder.value.id });
    }

    /* ---------- 我的 ---------- */
    async function loadHistory() {
      try {
        const res = await http('/user/order/historyOrders?page=1&pageSize=10');
        const rows = (res && res.records) || [];
        const map = { 1: '待付款', 2: '待接单', 3: '已接单', 4: '派送中', 5: '已完成', 6: '已取消' };
        historyList.value = rows.map(o => ({ ...o, statusText: map[o.status] || '未知' }));
        if (!rows.length) showToast('还没有历史订单');
      } catch (e) { showToast(e.message); }
    }

    /* ---------- AI 点单 ---------- */
    function scrollChat() {
      nextTick(() => {
        const el = document.querySelector('.chat-list');
        if (el) el.scrollTop = el.scrollHeight;
      });
    }

    async function sendAi() {
      const text = aiInput.value.trim();
      if (!text || aiLoading.value) return;

      aiMessages.value.push({ role: 'user', text });
      aiInput.value = '';
      aiLoading.value = true;
      scrollChat();

      // ★ 只传【这一条新消息】，不传历史。
      //   历史由 Python 侧的 checkpointer 按 sessionId 维护 ——
      //   如果我们还传全部历史，它会和 checkpointer 里的重复累积，
      //   token 越滚越多，而且模型会看到两遍同样的话。
      const messages = [{ role: 'user', content: text }];

      try {
        const res = await http('/user/ai/chat', {
          method: 'POST',
          body: { messages, sessionId: aiSessionId.value },
          auth: true,
        });
        // 模型偶尔会输出 **加粗** 这类 Markdown，聊天气泡是纯文本（pre-wrap），
        // 直接显示会露出星号。这里去掉加粗标记，保留换行。
        if (res.reply) aiMessages.value.push({ role: 'ai', text: res.reply.replace(/\*\*/g, '') });

        // ★ AI 可能在后台调用了 add_to_cart —— 刷新购物车，
        //   让底部的数量/金额角标和"我的"页面同步过来。
        await loadCart();
      } catch (e) {
        aiMessages.value.push({
          role: 'ai',
          text: 'AI 服务暂时不可用：' + e.message + '\n（检查 Python 服务是否已启动、Java 后端是否在跑）',
        });
      } finally {
        aiLoading.value = false;
        scrollChat();
      }
    }

    /** 清空当前会话：换一个 sessionId，并清掉界面上的消息。 */
    function resetAi() {
      aiSessionId.value = newSessionId();
      localStorage.setItem(AI_SESSION_KEY, aiSessionId.value);
      aiMessages.value = [{
        role: 'ai',
        text: '你好，我是食刻的 AI 点单助手。\n可以直接告诉我口味和预算，比如「想吃辣的，30块以内」。',
        welcome: true,
      }];
    }

    /* ---------- 页面切换 ---------- */
    const TITLES = {
      home: '食刻外卖', order: '确认订单', ai: 'AI 点单', my: '我的',
      address: '地址管理', addrEdit: '编辑地址',
      orderDetail: '订单详情', success: '下单成功',
    };
    function go(p) {
      page.value = p;
      title.value = TITLES[p] || '食刻外卖';
      if (p === 'my') loadHistory();
      if (p === 'ai') scrollChat();
    }

    // 三个主页面（有 tab 的）不显示返回箭头，其余都是子页面
    const canBack = computed(() => !['home', 'ai', 'my'].includes(page.value));

    function goBack() {
      if (page.value === 'addrEdit') return go('address');
      if (page.value === 'orderDetail') return go('my');
      go('home');
    }

    /* ---------- 启动 ---------- */
    onMounted(async () => {
      try {
        await initUser();
      } catch (e) {
        showToast('登录失败：' + e.message + '（后端起了吗？）');
      }
      await loadShopStatus();
      try {
        await loadCart();
        await loadCategories();
      } catch (e) {
        showToast(e.message);
      }

      // 支持直接打开某个页面：/app/#ai、/app/#my、/app/#order、/app/#address
      //                       /app/#orderDetail/3（带订单 id）
      // 用途：演示时可以直接甩一个页面链接给面试官，省得他找入口。
      const hash = (location.hash || '').replace('#', '');
      const [name, arg] = hash.split('/');
      if (name === 'order') goOrder();
      else if (name === 'address') goAddress();
      else if (name === 'orderDetail' && arg) openOrderDetail({ id: arg });
      else if (name === 'my') { go('my'); loadHistory(); }
      else if (TITLES[name]) go(name);
    });

    return {
      page, title, toast, loading,
      shopStatus, typeListData, typeIndex, dishListItems, cart, cartCount, cartTotal,
      userInfo, historyList,
      aiMessages, aiInput, aiLoading, sendAi, resetAi,
      money, onDishImgError, switchType, addDish, subDish, goOrder, loadHistory, go,
      canBack, goBack,
      address, addrForm, remark, submitting, submitOrder, ADDR_LABELS,
      // 购物车弹层
      showCart, openCart, closeCart, clearCart, cartAdd, cartSub, checkoutFromCart,
      // 菜品详情 / 口味
      showDish, dishDetail, flavorSel, parseFlavors, openDishDetail, closeDish,
      pickFlavor, onPopImgError, addWithFlavor,
      // 地址管理
      addressList, editingId, goAddress, newAddress, editAddress,
      saveAddrForm, removeAddress, removeEditing, setDefaultAddr, useAddress,
      // 订单详情 / 成功页
      orderDetail, lastOrder, statusText, openOrderDetail,
      paidCurrent, cancelCurrent, remindCurrent, againCurrent, viewLastOrder,
    };
  },
}).mount('#app');
