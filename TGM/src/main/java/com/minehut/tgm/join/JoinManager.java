package com.minehut.tgm.join;

import com.minehut.teamapi.models.UserProfile;
import com.minehut.teamapi.models.PlayerLogin;
import com.minehut.tgm.TGM;
import com.minehut.tgm.match.MatchPostLoadEvent;
import com.minehut.tgm.user.PlayerContext;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Created by luke on 4/27/17.
 */
public class JoinManager implements Listener {
    @Getter
    List<QueuedJoin> queuedJoins = new ArrayList<>();
    @Getter
    private List<LoginService> loginServices = new ArrayList<>();

    public JoinManager() {
        TGM.registerEvents(this);

        //empty queued joins when the connection didn't follow through for an unknown reason.
        Bukkit.getScheduler().scheduleSyncRepeatingTask(TGM.get(), new Runnable() {
            @Override
            public void run() {
                List<QueuedJoin> toRemove = new ArrayList<>();
                for (QueuedJoin queuedJoin : queuedJoins) {
                    if (System.currentTimeMillis() - queuedJoin.getTime() > 10 * 1000) {
                        toRemove.add(queuedJoin);
                    }
                }
                for (QueuedJoin queuedJoin : toRemove) {
                    queuedJoins.remove(queuedJoin);
                }
            }
        }, 20 * 10L, 20 * 10L);
    }

    /**
     * Allow custom services to hook into the login event.
     * This can be used with an external tournament plugin.
     */
    public void addLoginService(LoginService loginService) {
        getLoginServices().add(loginService);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UserProfile userProfile = TGM.get().getTeamClient().login(new PlayerLogin(event.getName(), event.getUniqueId().toString(), event.getAddress().getHostAddress()));
        Bukkit.getLogger().info(userProfile.getName() + " " + userProfile.getId().toString());
        queuedJoins.add(new QueuedJoin(event.getUniqueId(), userProfile, System.currentTimeMillis()));
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        QueuedJoin queuedJoin = getQueuedUserProfile(event.getPlayer());
        if(queuedJoin == null) {
            event.setResult(PlayerLoginEvent.Result.KICK_OTHER);
            event.setKickMessage(ChatColor.RED + "Unable to load user profile. Please try again.");
            return;
        }

        PlayerContext playerContext = new PlayerContext(event.getPlayer(), queuedJoin.getUserProfile());
        TGM.get().getPlayerManager().addPlayer(playerContext);

        for (LoginService loginService : loginServices) {
            loginService.login(playerContext);
        }

        queuedJoins.remove(queuedJoin);
    }

    private QueuedJoin getQueuedUserProfile(Player player) {
        for (QueuedJoin queuedJoin : queuedJoins) {
            if (player.getUniqueId().equals(queuedJoin.getUuid())) {
                return queuedJoin;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerContext playerContext = TGM.get().getPlayerManager().getPlayerContext(event.getPlayer());
        Bukkit.getPluginManager().callEvent(new MatchJoinEvent(playerContext));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCycle(MatchPostLoadEvent event) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerContext playerContext = TGM.get().getPlayerManager().getPlayerContext(player);
            Bukkit.getPluginManager().callEvent(new MatchJoinEvent(playerContext));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLeave(PlayerQuitEvent event) {
        handleQuit(event.getPlayer());
    }

    public void handleQuit(Player player) {
        TGM.get().getPlayerManager().removePlayer(TGM.get().getPlayerManager().getPlayerContext(player));
    }

    @AllArgsConstructor
    private class QueuedJoin {
        @Getter UUID uuid;
        @Getter UserProfile userProfile;
        @Getter long time;
    }

}
