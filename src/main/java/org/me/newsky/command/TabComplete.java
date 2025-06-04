package org.me.newsky.command;

import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * 如果子指令需要自訂 Tab 補全，實作此介面並覆寫 tabComplete 方法即可。
 */
public interface TabComplete {
    /**
     * @param sender 執行者 (玩家或 Console)
     * @param label  主指令標籤 (例如 "is" 或 "isadmin")
     * @param args   已輸入的參數陣列，args[0] 是子指令名稱，args[1...] 是後續參數
     * @return 建議補全字串清單
     */
    List<String> tabComplete(CommandSender sender, String label, String[] args);
}
