/**
 * Chụp giao diện app HCC ở đúng khung màn BellaBot Pro (1280×800 @160dpi) để đưa
 * vào catalog. Chụp từ CHÍNH assets/index.html đang đóng gói trong APK — không
 * dựng lại giao diện riêng cho tài liệu, để ảnh trong catalog luôn là cái người
 * dân thật sự nhìn thấy.
 *
 *   node tools/chup-man-hinh-catalog.mjs [thu-muc-ra]
 *
 * Trên máy tính không có cầu nối CAU của Kotlin, nên script tự dựng một CAU giả
 * lập TỐI THIỂU: đủ để nút "Robot dẫn tôi tới quầy", nút micro và nút Lấy số hiện
 * ra đúng như trên robot. Mọi nội dung chữ vẫn do app tự sinh từ 165 thủ tục thật.
 *
 * Playwright mượn của tools/html-video (giống tools/render-video.mjs), không cài riêng.
 */
import { existsSync, mkdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const APP  = resolve(HERE, '..');
const REPO = resolve(APP, '..', '..');
const PW = join(REPO, 'tools', 'html-video', 'packages',
                'adapter-hyperframes', 'node_modules', 'playwright', 'index.mjs');
if (!existsSync(PW)) { console.error('Khong thay playwright tai:\n  ' + PW); process.exit(1); }
const { chromium } = await import(pathToFileURL(PW).href);

const TRANG = join(APP, 'app', 'src', 'main', 'assets', 'index.html');
const RA = resolve(process.argv[2] || join(APP, 'anh-catalog'));
mkdirSync(RA, { recursive: true });

/* CAU giả lập — cùng bộ hàm mà index.html dò tìm (grep "CAU\." ra 13 hàm). */
const CAU_GIA_LAP = () => {
  window.CAU = {
    doc: t => { window.__docCuoi = t; setTimeout(() => window.robotDocXong && window.robotDocXong(), 60); },
    dungDoc: () => {},
    laySo: (id, ten, quay) => JSON.stringify({ so: 27, ten, quay, cho_truoc: 3 }),
    traSo: () => '',
    danDuongToiQuay: q => { window.baoDanDuong('bat-dau'); },
    dungDanDuong: () => {},
    batMic: () => {}, tatMic: () => {},
    /* Robot thật đẩy câu trả lời ngược lên qua window.nhanLoiNoi. Giả lập đúng
       đường đó, nội dung lấy từ bộ trả lời nội bộ của chính app. */
    hoiRobot: q => setTimeout(() => {
      const tl = window.traLoiNoiBo ? window.traLoiNoiBo(q) : { chu: '' };
      window.nhanLoiNoi('robot', tl.chu, true);
    }, 500),
    xoaNguCanh: () => {}, moTaManHinh: () => {},
    thongTinAI: () => '{}',
    diaChiMayChu: () => '192.168.1.183:8080',
  };
};

/* [ten file, mo ta, viec lam trong trang, cho them ms] */
const BUOC = [
  ['01-man-cho',        'Màn chờ — clip biểu cảm robot', null, 1500],
  ['02-chon-viec',      'Màn chọn việc: Tư vấn / Lấy số', () => { thoiCho(); }, 500],
  ['02b-trang-chu',     'Trang chủ — 11 nhóm + ô tìm kiếm', () => { veTrangChu(); }, 500],
  ['03-menu-11-nhom',   'Menu tầng 1 — 11 nhóm lĩnh vực', () => { batDauViec('tu-van'); }, 500],
  ['04-nhom-con',       'Menu tầng 2 — nhóm con', () => { batDauViec('tu-van'); moNhom(D.nhom[0].ma); }, 500],
  ['05-danh-sach-the',  'Menu tầng 3 — thẻ thủ tục (nhóm con đông nhất)', () => {
      batDauViec('tu-van');
      /* Lấy nhóm con nhiều thẻ nhất để ảnh minh hoạ không ra một dòng trơ trọi. */
      let tot = null, nhieu = 0;
      for (const ma of Object.keys(D.nhom_con || {})) {
        for (const nc of D.nhom_con[ma]) {
          const n = TT.filter(t => t.nhom === ma && t.nhom_con === nc.ma).length;
          if (n > nhieu) { nhieu = n; tot = [ma, nc.ma]; }
        }
      }
      if (tot) moNhomCon(tot[0], tot[1]); else moNhom(D.nhom[0].ma);
    }, 500],
  ['05b-so-do-quay',    'Sơ đồ 7 quầy của Trung tâm', () => { batDauViec('tu-van'); moDsQuay(); }, 500],
  ['06-chi-tiet-thu-tuc', 'Chi tiết thủ tục + bảng quầy', () => {
      const t = TT.find(x => (x.doc || '').length > 400) || TT[0];
      moChiTiet(t.id);
    }, 600],
  ['07-tim-kiem',       'Ô tìm kiếm — gõ chữ ra kết quả', () => {
      veTrangChu(); batDauViec('tu-van');
      const o = document.querySelector('#o-nhap');
      if (o) { o.value = 'đất đai'; goTim(); }
    }, 700],
  ['08-dan-duong',      'Robot đang dẫn đường tới quầy', () => {
      const t = TT.find(x => x.quay) || TT[0];
      moChiTiet(t.id); window.baoDanDuong('bat-dau');
    }, 900],
  ['09-lay-so',         'Phiếu số thứ tự trên màn hình', () => {
      const t = TT.find(x => x.quay) || TT[0];
      moChiTiet(t.id); laySoChoThuTuc();
    }, 700],
  ['10-tro-chuyen-ai',  'Màn trò chuyện AI — robot chào', () => { moAI(); }, 800],
  ['11-ai-tra-loi',     'AI trả lời câu hỏi về số quầy', () => {
      moAI();
      const o = document.querySelector('#ai-o');
      if (o) { o.value = 'trung tâm này có mấy quầy'; guiAI(); }
    }, 3000],
];

const tb = await chromium.launch();
const ctx = await tb.newContext({ viewport: { width: 1280, height: 800 }, deviceScaleFactor: 2 });
const p = await ctx.newPage();
await p.addInitScript(CAU_GIA_LAP);
const loi = [];
p.on('pageerror', e => loi.push(String(e)));
await p.goto(pathToFileURL(TRANG).href);
await p.waitForTimeout(1200);

for (const [ten, mo, viec, cho] of BUOC) {
  if (viec) {
    try { await p.evaluate(viec); }
    catch (e) { console.log(`  ! ${ten}: ${String(e).split('\n')[0]}`); }
  }
  await p.waitForTimeout(cho || 400);
  const f = join(RA, ten + '.png');
  await p.screenshot({ path: f });
  console.log(`  ✓ ${ten}.png   ${mo}`);
  /* Về màn chờ giữa các bước để trạng thái không dính sang nhau. */
  try { await p.evaluate(() => veManCho()); } catch (_) {}
  await p.waitForTimeout(250);
}

if (loi.length) { console.log('\nLoi JavaScript:'); loi.forEach(l => console.log('  ' + l.split('\n')[0])); }
console.log('\nAnh o: ' + RA);
await tb.close();
