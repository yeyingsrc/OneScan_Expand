package burp.vaycore.onescan.common;

import java.util.regex.Pattern;

/**
 * 常量
 * <p>
 * Created by vaycore on 2022-08-07.
 */
public interface Constants {

    // 插件信息
    String PLUGIN_NAME = "OneScan_Expand";
    String PLUGIN_VERSION = "1.1.5";
    boolean DEBUG = false;

    // 插件启动显示的信息
    String BANNER = "#" +
            "#############################################\n" +
            "  " + PLUGIN_NAME + " v" + PLUGIN_VERSION + "\n" +
            "  Author:    0ne_1\n" +
            "  Developer: vaycore\n" +
            "  Developer: Rural.Dog\n" +
            "  Developer:人间小福星\n"+
            "  Github: https://github.com/Zmz-c/OneScan_Expand\n" +
            "  Note:新增数据储存功能，历史数据不再丢失，可选数据储存时间，全面升级到JDK21，不再支持jdk1.8,性能优化\n" +
            "#############################################\n";

    // 插件卸载显示的信息
    String UNLOAD_BANNER = "\n" +
            "###########################################################################\n" +
            "  " + PLUGIN_NAME + " uninstallation completed, thank you for your attention and use." + "\n" +
            "###########################################################################\n";

    // 匹配请求行的 URL 位置
    Pattern REGEX_REQ_LINE_URL = Pattern.compile("[A-Z]+\\s+(.*?)\\s+HTTP/", Pattern.CASE_INSENSITIVE);
}
