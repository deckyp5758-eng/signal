package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.service.GitHubRelease
import com.example.service.UpdateState
import com.example.ui.theme.*

data class TutorialModule(
    val id: Int,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentColor: Color,
    val contents: List<String>
)

@Composable
fun TutorialScreen(
    updateState: UpdateState = UpdateState.Idle,
    repoOwner: String = "kingdomedantech",
    repoName: String = "scalpsignal-app",
    onCheckUpdate: () -> Unit = {},
    onUpdateRepoConfig: (String, String) -> Unit = { _, _ -> },
    onOpenUrl: (String) -> Unit = {}
) {
    var expandedModuleId by remember { mutableStateOf(1) }

    // Custom repo config dialog state
    var showRepoDialog by remember { mutableStateOf(false) }
    var inputOwner by remember(repoOwner) { mutableStateOf(repoOwner) }
    var inputRepo by remember(repoName) { mutableStateOf(repoName) }

    // Pre-flight discipline checklist states
    var check1 by remember { mutableStateOf(false) }
    var check2 by remember { mutableStateOf(false) }
    var check3 by remember { mutableStateOf(false) }
    var check4 by remember { mutableStateOf(false) }

    val modules = remember {
        listOf(
            TutorialModule(
                id = 1,
                title = "Dasar Scalping XAU/USD & EUR/USD",
                subtitle = "Pahami karakteristik pergerakan harga emas dan mata uang euro",
                icon = Icons.Default.TrendingUp,
                accentColor = GoldPrimary,
                contents = listOf(
                    "Scalping adalah metode trading cepat di mana posisi dibuka dan ditutup dalam rentang waktu singkat (1 hingga 15 menit) untuk menangkap pergerakan harga kecil namun konsisten.",
                    "XAU/USD (Emas): Memiliki volatilitas tinggi dengan pergerakan harian yang lebar (100 - 300 pips). Sangat disukai scalper karena pergerakan tren yang eksplosif, namun membutuhkan Stop Loss yang disiplin.",
                    "EUR/USD: Pasangan mata uang paling likuid di dunia dengan spread terendah (0.1 - 0.5 pips). Pergerakannya lebih tenang, menjadikannya pilihan ideal bagi pemula yang baru belajar scalping.",
                    "Semua fitur sinyal dan kalkulator di aplikasi ScalpSignal ini 100% gratis digunakan tanpa biaya berlangganan."
                )
            ),
            TutorialModule(
                id = 2,
                title = "Cara Membaca Sinyal & Indikator",
                subtitle = "Kombinasi EMA 9/21, RSI, dan MACD untuk konfirmasi entry",
                icon = Icons.Default.Analytics,
                accentColor = CyanEma,
                contents = listOf(
                    "EMA 9 & EMA 21 (Moving Average): Garis biru (EMA 9) memotong ke atas garis oranye (EMA 21) disebut 'Golden Cross' = konfirmasi kuat untuk BUY. Sebaliknya jika memotong ke bawah disebut 'Death Cross' = konfirmasi untuk SELL.",
                    "RSI (Relative Strength Index): Angka di bawah 30 menandakan harga jenuh jual (Oversold), siap berbalik naik. Angka di atas 70 menandakan jenuh beli (Overbought), siap koreksi turun.",
                    "Sinyal STRONG BUY/SELL: Muncul saat arah EMA, RSI, dan MACD berada dalam satu frekuensi kesepakatan momentum yang sama (Akurasi di atas 85%).",
                    "Jangan pernah entry melawan arah sinyal utama yang tertera pada kartu sinyal."
                )
            ),
            TutorialModule(
                id = 3,
                title = "Aturan Emas Manajemen Risiko (SL & TP)",
                subtitle = "Kunci menjadi scalper konsisten tanpa bangkrut",
                icon = Icons.Default.Shield,
                accentColor = BuyGreen,
                contents = listOf(
                    "Aturan Risiko 1% - 2%: Jangan pernah merisikokan lebih dari 1% sampai 2% dari saldo akun Anda dalam satu posisi trading!",
                    "Multi-Mata Uang & Tipe Akun: Kalkulator kami mendukung akun USD ($), Rupiah (IDR), dan Euro (EUR). Anda juga bisa memilih tipe Akun Standar, Cent/Mikro, atau Mini.",
                    "Akun Cent (Cocok Modal Kecil): Pada Akun Cent, 1 Lot bernilai 1.000 unit ($0.10/pip), sehingga modal Rp 100.000 - Rp 1.000.000 bisa trading dengan lot aman tanpa takut margin call.",
                    "Akun Standar: 1 Lot bernilai 100.000 unit ($10.00/pip). Disarankan untuk modal di atas $500 (Rp 8.000.000+).",
                    "Rasio Risk to Reward (R:R) Minimal 1:1.5: Artinya potensi keuntungan selalu lebih besar daripada risiko kerugian. Bahkan jika win-rate Anda 50%, akun tetap bertumbuh positif.",
                    "Stop Loss (SL) Wajib Dipasang: Jangan pernah trading tanpa SL di pasar scalping emas, karena volatilitas tinggi bisa menguras modal jika tidak dibatasi."
                )
            ),
            TutorialModule(
                id = 4,
                title = "Panduan Memasang Order di MetaTrader 4/5",
                subtitle = "Langkah demi langkah mengeksekusi sinyal ke broker Anda",
                icon = Icons.Default.Smartphone,
                accentColor = EuroBlue,
                contents = listOf(
                    "1. Buka aplikasi MetaTrader 4 atau 5 di smartphone Anda.",
                    "2. Buka tab 'Quotes' lalu pilih instrumen 'XAUUSD' atau 'EURUSD'.",
                    "3. Tekan 'New Order / Transaksi Baru'.",
                    "4. Atur ukuran lot sesuai hasil 'Kalkulator Risiko' di aplikasi ScalpSignal (misal: 0.04 Lot).",
                    "5. Tekan tombol 'Salin' di aplikasi ini, lalu paste Stop Loss ke kolom garis merah dan Take Profit ke kolom garis hijau di MetaTrader.",
                    "6. Tekan tombol 'BUY by Market' atau 'SELL by Market' sesuai arahan sinyal."
                )
            ),
            TutorialModule(
                id = 5,
                title = "Waktu & Sesi Terbaik untuk Scalping",
                subtitle = "Ketahui kapan pasar paling aktif dan likuid",
                icon = Icons.Default.Schedule,
                accentColor = OrangeEma,
                contents = listOf(
                    "Sesi London (14:00 - 18:00 WIB): Waktu terbaik untuk scalping EUR/USD karena volume transaksi perbankan Eropa mulai ramai.",
                    "Sesi New York (19:30 - 23:30 WIB): Waktu paling volatil dan terbaik untuk scalping XAU/USD (Emas) saat sesi London dan New York tumpang tindih (overlap).",
                    "Peringatan Berita Ekonomi (High Impact News): Hindari membuka posisi scalping 15 menit sebelum dan sesudah rilis berita besar seperti US CPI, NFP, atau suku bunga The Fed (FOMC)."
                )
            ),
            TutorialModule(
                id = 6,
                title = "Kamus Istilah Scalper Pemula",
                subtitle = "Pahami bahasa trader profesional",
                icon = Icons.Default.MenuBook,
                accentColor = PurpleMacd,
                contents = listOf(
                    "Pip (Point in Percentage): Satuan ukuran terkecil pergerakan harga. Pada emas, $0.10 setara 1 pip. Pada EUR/USD, 0.00010 setara 1 pip.",
                    "Lot: Satuan volume transaksi yang dibuka di pasar. 0.01 lot adalah ukuran micro lot terkecil yang aman untuk pemula.",
                    "Spread: Selisih antara harga Beli (Ask) dan Jual (Bid) yang menjadi komisi broker.",
                    "Margin Call: Peringatan saat modal tidak cukup lagi menahan floating loss akibat tidak memasang Stop Loss."
                )
            ),
            TutorialModule(
                id = 7,
                title = "Panduan Install & Update APK dari GitHub",
                subtitle = "Cara download APK dan memasangnya di HP Android Anda",
                icon = Icons.Default.DownloadForOffline,
                accentColor = CyanEma,
                contents = listOf(
                    "1. Hubungkan Proyek ke GitHub: Di AI Studio, klik menu 'Settings' atau 'Export' lalu pilih 'Push to GitHub' untuk mengunggah kode ke akun GitHub Anda.",
                    "2. Build Otomatis Berjalan: File .github/workflows/build-apk.yml akan otomatis memproses dan meng-compile file APK setiap kali Anda memperbarui kode di GitHub.",
                    "3. Download File APK: Buka tab 'Releases' di repositori GitHub Anda, atau tekan tombol 'Download APK' pada kartu updater di atas.",
                    "4. Instalasi di Android: Buka file 'ScalpSignal-Latest.apk' yang telah diunduh di HP Anda, izinkan 'Install from Unknown Sources' jika diminta, lalu tekan 'Install'.",
                    "5. Otomatis Terhubung: Saat ada versi update baru di kemudian hari, Anda cukup menekan tombol 'Cek Rilis GitHub' di layar ini untuk memperbarui aplikasi secara instan."
                )
            )
        )
    }

    // Dialog for changing GitHub repo configuration
    if (showRepoDialog) {
        AlertDialog(
            onDismissRequest = { showRepoDialog = false },
            title = {
                Text("Atur Repositori GitHub", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Masukkan nama Username GitHub & nama Repository tempat kode aplikasi ini disimpan:",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = inputOwner,
                        onValueChange = { inputOwner = it },
                        label = { Text("GitHub Username / Owner") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputRepo,
                        onValueChange = { inputRepo = it },
                        label = { Text("Repository Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateRepoConfig(inputOwner, inputRepo)
                        showRepoDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Simpan", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRepoDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
    ) {
        // GitHub APK Auto-Update & Install Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .testTag("card_github_updater")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GoldPrimary.copy(alpha = 0.2f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "GitHub Update",
                                    tint = GoldPrimary,
                                    modifier = Modifier.padding(6.dp).size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Update & Download via GitHub",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Versi Terpasang: v${BuildConfig.VERSION_NAME}",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        IconButton(
                            onClick = { showRepoDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Pengaturan Repo",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Target repo chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Repo: github.com/$repoOwner/$repoName",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                            Text(
                                text = "Ubah",
                                fontSize = 11.sp,
                                color = CyanEma,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showRepoDialog = true }
                            )
                        }
                    }

                    // Update Status / Result
                    when (updateState) {
                        is UpdateState.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = GoldPrimary)
                                Text("Memeriksa rilis terbaru di GitHub...", fontSize = 12.sp, color = TextSecondary)
                            }
                        }
                        is UpdateState.UpToDate -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BuyGreenContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = BuyGreen, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Aplikasi Anda sudah versi paling baru (v${BuildConfig.VERSION_NAME})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = BuyGreen
                                    )
                                }
                            }
                        }
                        is UpdateState.Available -> {
                            val rel = updateState.release
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = GoldPrimary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, GoldPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🎉 Versi Baru: ${rel.tagName}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldPrimary
                                        )
                                        Text(
                                            text = rel.name,
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }
                                    Text(
                                        text = rel.changelog.take(200),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        lineHeight = 15.sp
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                rel.downloadUrl?.let { onOpenUrl(it) }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(imageVector = Icons.Default.Download, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Download APK", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        is UpdateState.Error -> {
                            Text(
                                text = updateState.message,
                                fontSize = 11.sp,
                                color = SellRed,
                                lineHeight = 15.sp
                            )
                        }
                        UpdateState.Idle -> {}
                    }

                    // Check Button & Direct GitHub Releases Link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onCheckUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cek Rilis GitHub", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                onOpenUrl("https://github.com/$repoOwner/$repoName/releases")
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, tint = CyanEma, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Releases", color = CyanEma, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Free Access Guarantee Hero Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, FreeBadgeGreen.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(FreeBadgeBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "100% GRATIS SELAMANYA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = FreeBadgeGreen
                            )
                        }
                        Text(
                            text = "Akses Penuh Tanpa Bayar",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Aplikasi ScalpSignal dirancang untuk membantu trader pemula mendapatkan sinyal indikator real-time, manajemen risiko SL/TP otomatis, dan edukasi pasar tanpa biaya tersembunyi.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Section Title
        item {
            Text(
                text = "Modul Edukasi Scalping Pemula",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Expandable Tutorial Cards
        items(modules) { module ->
            val isExpanded = expandedModuleId == module.id

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isExpanded) module.accentColor.copy(alpha = 0.6f) else DarkBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        expandedModuleId = if (isExpanded) 0 else module.id
                    }
                    .testTag("tutorial_module_${module.id}")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(module.accentColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = module.icon,
                                    contentDescription = null,
                                    tint = module.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = module.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = module.subtitle,
                                    fontSize = 11.sp,
                                    color = TextSecondary,
                                    maxLines = if (isExpanded) 2 else 1
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Divider(color = DarkBorder, thickness = 0.5.dp)
                            module.contents.forEach { point ->
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(module.accentColor)
                                    )
                                    Text(
                                        text = point,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 17.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Interactive Pre-Flight Discipline Checklist
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .testTag("card_preflight_checklist")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FactCheck, contentDescription = null, tint = GoldPrimary)
                        Text(
                            text = "Checklist Disiplin Sebelum Buka Posisi",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Centang semua poin di bawah sebelum mengeksekusi order:",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )

                    ChecklistItem(
                        text = "Saya sudah menghitung ukuran lot aman sesuai modal saya (risiko <= 2%)",
                        checked = check1,
                        onCheckedChange = { check1 = it }
                    )
                    ChecklistItem(
                        text = "Saya sudah mencatat harga Stop Loss (SL) dan Take Profit (TP) otomatis",
                        checked = check2,
                        onCheckedChange = { check2 = it }
                    )
                    ChecklistItem(
                        text = "Arah sinyal (BUY/SELL) sudah sesuai konfirmasi EMA 9/21 dan RSI",
                        checked = check3,
                        onCheckedChange = { check3 = it }
                    )
                    ChecklistItem(
                        text = "Tidak ada berita ekonomi berdampak tinggi (High Impact News) dalam 15 menit ke depan",
                        checked = check4,
                        onCheckedChange = { check4 = it }
                    )

                    val allChecked = check1 && check2 && check3 && check4
                    if (allChecked) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BuyGreenContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = BuyGreen)
                                Text(
                                    text = "Siap Trading! Disiplin adalah kunci konsistensi Anda.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BuyGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChecklistItem(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = GoldPrimary,
                checkmarkColor = Color.Black
            )
        )
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (checked) MaterialTheme.colorScheme.onSurface else TextSecondary,
            modifier = Modifier.weight(1f),
            lineHeight = 16.sp
        )
    }
}
