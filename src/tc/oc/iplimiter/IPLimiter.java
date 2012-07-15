package tc.oc.iplimiter;

import java.util.List;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;

public class IPLimiter extends JavaPlugin implements Listener {
    public String KICK_MESSAGE = ChatColor.RED + "Your IP has not been approved - Contact the server owner if this is in error";

    @Override
    public void onEnable() {
        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void login(final PlayerLoginEvent event) {
        String playerName = event.getPlayer().getName();
        String ip = event.getAddress().getHostAddress();
        if(this.isIPAllowedForPlayer(playerName, ip)) {
            this.getLogger().info("Allowing " + playerName + " to login from " + ip);
        } else {
            this.getLogger().info("Denied " + playerName + " from logging in from " + ip);
            event.disallow(Result.KICK_WHITELIST, this.KICK_MESSAGE);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if(args.length > 0) {
            if(args[0].equalsIgnoreCase("reload")) {
                if(sender.hasPermission("iplimiter.reload")) {
                    this.reloadConfig();
                    sender.sendMessage(ChatColor.GREEN + "Configuration reloaded");
                } else {
                    sender.sendMessage(ChatColor.RED + "No permission");
                }
            } else if (args[0].equalsIgnoreCase("info")) {
                if(sender.hasPermission("iplimiter.info")) {
                    if(args.length > 1) {
                        String playerName = args[1].toLowerCase();
                        List<String> ips = this.getPlayerIPs(playerName);
                        if(ips.size() > 0) {
                            sender.sendMessage("Allowed IP addresses " + playerName + " may join from:");
                            for(String ip : ips) {
                                sender.sendMessage("- " + ip);
                            }
                        } else {
                            sender.sendMessage(playerName + " may join from any IP address");
                        }
                    } else {
                        Set<String> limitedPlayers = this.getConfig().getKeys(false);
                        sender.sendMessage("IPLimiter currently limiting " + limitedPlayers.size() + " players from joining from any IP address:");
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "No permission");
                }
            } else if (args[0].equalsIgnoreCase("list")) {
                if(sender.hasPermission("iplimiter.list")) {
                    int resultsPerPage = 10;
                    int page = 1;
                    if(args.length > 1) {
                        try {
                            page = Integer.parseInt(args[1]);
                        } catch (NumberFormatException e) {
                            // ignore exception
                        }
                    }
                    Set<String> limitedPlayers = this.getConfig().getKeys(false);
                    sender.sendMessage("Limited players (Page " + page + " of " + ((limitedPlayers.size() - 1) / resultsPerPage + 1) + "):");
                    int i = 1;
                    for(String playerName : limitedPlayers) {
                        if(i > (page - 1) * resultsPerPage) {
                            sender.sendMessage(i + ". " + playerName);
                        }
                        i++;
                        if(i > page * resultsPerPage) {
                            break;
                        }
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "No permission");
                }
            } else if (args[0].equalsIgnoreCase("purge")) {
                if(sender.hasPermission("iplimiter.purge")) {
                    for(Player player : this.getServer().getOnlinePlayers()) {
                        if(!this.isPlayerAllowed(player)) {
                            this.getLogger().info("Kicked " + player.getName() + " for being logged in from an illegal address: " + getPlayerIPAddress(player));
                            player.kickPlayer(this.KICK_MESSAGE);
                        }
                    }
                    sender.sendMessage(ChatColor.GREEN + "Server has been purged of players logged in from illegal IP addresses.");
                } else {
                    sender.sendMessage(ChatColor.RED + "No permission");
                }
            } else if (args.length > 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
                if(sender instanceof ConsoleCommandSender) {
                    String playerName = args[1].toLowerCase();
                    String ip = args[2];
                    if(args[0].equalsIgnoreCase("add")) {
                        if(this.isIPAllowedForPlayer(playerName, ip, true)) {
                            sender.sendMessage(ChatColor.RED + playerName + " may already join from " + ip);
                        } else {
                            List<String> ips = this.getPlayerIPs(playerName);
                            ips.add(ip);
                            this.savePlayerIPs(playerName, ips);
                            sender.sendMessage(ChatColor.GREEN + "Allowing " + playerName + " to join from " + ip);
                        }
                    } else {
                        if(!this.isIPAllowedForPlayer(playerName, ip, true)) {
                            sender.sendMessage(ChatColor.RED + playerName + " is already not allowed to join from " + ip);
                        } else {
                            List<String> ips = this.getPlayerIPs(playerName);
                            ips.remove(ip);
                            this.savePlayerIPs(playerName, ips);
                            sender.sendMessage(ChatColor.GREEN + "Denying " + playerName + " to join from " + ip);
                        }
                    }
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
        return true;
    }

    private static String getPlayerIPAddress(Player player) {
        return player.getAddress().getAddress().getHostAddress();
    }

    private boolean isPlayerAllowed(Player player) {
        return this.isIPAllowedForPlayer(player.getName(), getPlayerIPAddress(player));
    }

    private boolean isIPAllowedForPlayer(String playerName, String ip) {
        return this.isIPAllowedForPlayer(playerName, ip, false);
    }

    private boolean isIPAllowedForPlayer(String playerName, String ip, boolean explicit) {
        List<String> ips = this.getPlayerIPs(playerName);
        if(ips.size() > 0 || explicit) {
            for(String allowedIP : this.getPlayerIPs(playerName)) {
                if(ip.equals(allowedIP)) {
                    return true;
                }
            }
            return false;
        } else {
            return true;
        }
    }

    private List<String> getPlayerIPs(String playerName) {
        return this.getConfig().getStringList(playerName.toLowerCase());
    }

    private void savePlayerIPs(String playerName, List<String> ips) {
        this.getConfig().set(playerName.toLowerCase(), ips);
        this.saveConfig();
    }
}