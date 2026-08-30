# Net-USTB R8 规则（v1.0.13 起启用混淆 + 资源收缩）
#
# 排查结论：Compose / Coroutines / OkHttp / OkIO / DataStore / WorkManager /
# Vico / security-crypto 均自带 consumer rules，无需重复声明。
# 这里只补本项目特有的反射点与已知缺口。

# ---------- 项目反射点 ----------

# WorkManager 通过反射实例化 Worker（保留构造器签名）
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Manifest 组件（AAPT 已 keep，此处显式保底，防止配置变更遗漏）
-keep class com.example.netconnect_tool.widget.TrafficWidgetProvider { *; }
-keep class com.example.netconnect_tool.MainActivity { *; }

# 数据模型：虽然当前用 org.json 手动序列化（无反射），
# 但保留字段名可让 R8 崩溃栈与排查更直观，体积代价可忽略
-keep class com.example.netconnect_tool.data.model.** { *; }

# ---------- 第三方缺口 ----------

# Room 通过反射实例化 *_Impl 的无参构造器（WorkManager 内嵌的 WorkDatabase 同理）。
# 缺这条规则 R8 会裁掉构造器 → 启动即闪退 NoSuchMethodException WorkDatabase_Impl.<init>
-keep class * extends androidx.room.RoomDatabase {
    public <init>();
}
-dontwarn androidx.room.paging.**

# jsoup 1.18 未携带 consumer rules
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# security-crypto 依赖的注解库在编译期存在、运行期可缺省
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**

# ---------- 崩溃栈可读性 ----------

# 保留源码行号，release 崩溃日志可直接对照（配合 mapping.txt 反混淆）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
