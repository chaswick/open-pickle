package com.w3llspring.fhpb.web.db;

import com.w3llspring.fhpb.web.model.UserCredit;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserCreditRepository extends JpaRepository<UserCredit, Long> {
  List<UserCredit> findByMatchId(Long matchId);
}
