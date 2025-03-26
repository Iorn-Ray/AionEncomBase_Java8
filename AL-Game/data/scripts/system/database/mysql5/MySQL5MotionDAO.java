/*
 * This file is part of Encom.
 *
 *  Encom is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Encom is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser Public License
 *  along with Encom.  If not, see <http://www.gnu.org/licenses/>.
 */
package mysql5;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.MotionDAO;
import com.aionemu.gameserver.dao.MySQL5DAOUtils;
import com.aionemu.gameserver.dao.PlayerEmotionListDAO;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.motion.Motion;
import com.aionemu.gameserver.model.gameobjects.player.motion.MotionList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException; // Импортируем класс SQLException

/**
 * @author MrPoke
 * @rework MATTY
 */
public class MySQL5MotionDAO extends MotionDAO {

	/** Logger */
	private static final Logger log = LoggerFactory.getLogger(PlayerEmotionListDAO.class);
	public static final String INSERT_QUERY = "INSERT INTO `player_motions` (`player_id`, `motion_id`, `active`,  `time`) VALUES (?,?,?,?)";
	public static final String SELECT_QUERY = "SELECT `motion_id`, `active`, `time` FROM `player_motions` WHERE `player_id`=?";
	public static final String DELETE_QUERY = "DELETE FROM `player_motions` WHERE `player_id`=? AND `motion_id`=?";
	public static final String UPDATE_QUERY = "UPDATE `player_motions` SET `active`=? WHERE `player_id`=? AND `motion_id`=?";

	@Override
	public boolean supports(String s, int i, int i1) {
		return MySQL5DAOUtils.supports(s, i, i1);
	}

	@Override
	public void loadMotionList(Player player) {
	    MotionList motions = new MotionList(player);
		List<Motion> loadedMotions = loadMotions(player.getObjectId());
		if (loadedMotions != null) {
			for (Motion motion : loadedMotions) {
				motions.add(motion, false);
			}
		}
		player.setMotions(motions);
	}
	
	@Override
	public List<Motion> loadMotions(Integer playerId) {
		Connection con = null;
		PreparedStatement stmt = null;
		ResultSet rset = null;
		List<Motion> motions = new ArrayList<>();

		try {
			con = DatabaseFactory.getConnection();
			stmt = con.prepareStatement(SELECT_QUERY);
			stmt.setInt(1, playerId);
			rset = stmt.executeQuery();

			while (rset.next()) {
				int motionId = rset.getInt("motion_id");
				int time = rset.getInt("time");
				boolean isActive = rset.getBoolean("active");
				motions.add(new Motion(motionId, time, isActive));
			}

		} catch (SQLException e) {
			log.error("Could not load motions for playerObjId: " + playerId + " from DB: " + e.getMessage(), e);
		} finally {
			try {
				if (rset != null) rset.close();
				if (stmt != null) stmt.close();
				if (con != null) DatabaseFactory.close(con);
			} catch (SQLException e) {
				log.error("Error: " + e.getMessage(), e);
			}
		}
		return motions;
	}

	@Override
	public boolean storeMotion(int objectId, Motion motion) {

		Connection con = null;
		PreparedStatement stmt = null; // Объявляем PreparedStatement
		try {
			con = DatabaseFactory.getConnection();
			stmt = con.prepareStatement(INSERT_QUERY);
			stmt.setInt(1, objectId);
			stmt.setInt(2, motion.getId());
			stmt.setBoolean(3, motion.isActive());
			stmt.setInt(4, motion.getExpireTime());
			stmt.execute();

		}
		catch (SQLException e) { // Ловим SQLException, а не Exception
			log.error("Could not store motion for player " + objectId + " from DB: " + e.getMessage(), e);
			return false;
		}
		finally {
			try { // Закрываем ресурсы в блоке finally с try-catch
				if (stmt != null) {
					stmt.close();
				}
				if (con != null) {
					DatabaseFactory.close(con);
				}
			} catch (SQLException e) {
				log.error("Ошибка при закрытии ресурсов: " + e.getMessage(), e);
			}
		}
		return true;
	}

	@Override
	public boolean deleteMotion(int objectId, int motionId) {
		Connection con = null;
		try {
			con = DatabaseFactory.getConnection();
			PreparedStatement stmt = con.prepareStatement(DELETE_QUERY);
			stmt.setInt(1, objectId);
			stmt.setInt(2, motionId);
			stmt.execute();
			stmt.close();
		}
		catch (Exception e) {
			log.error("Could not delete motion for player " + objectId + " from DB: " + e.getMessage(), e);
			return false;
		}
		finally {
			DatabaseFactory.close(con);
		}
		return true;
	}

	@Override
	public boolean updateMotion(int objectId, Motion motion) {
		Connection con = null;
		try {
			con = DatabaseFactory.getConnection();
			PreparedStatement stmt = con.prepareStatement(UPDATE_QUERY);
			stmt.setBoolean(1, motion.isActive());
			stmt.setInt(2, objectId);
			stmt.setInt(3, motion.getId());
			stmt.execute();
			stmt.close();
		}
		catch (Exception e) {
			log.error("Could not store motion for player " + objectId + " from DB: " + e.getMessage(), e);
			return false;
		}
		finally {
			DatabaseFactory.close(con);
		}
		return true;
	}
}