package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.UserStyle;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserStyleRepository extends JpaRepository<UserStyle, Long> {

  @Query(
      value =
          "SELECT us.* FROM user_style us JOIN (SELECT style_name, MAX(id) AS max_id FROM user_style WHERE style_owner = ?1 GROUP BY style_name) x ON x.max_id = us.id WHERE us.style_owner = ?1 ORDER BY us.style_name",
      nativeQuery = true)
  public List<UserStyle> findUserStyles(Long userId);

  @Query(
      value = "SELECT COUNT(*) FROM user_style WHERE style_owner = ?1 AND style_voter = ?2",
      nativeQuery = true)
  public Integer findIfVoted(Long styleOwnerId, Long styleVoterId);
}
