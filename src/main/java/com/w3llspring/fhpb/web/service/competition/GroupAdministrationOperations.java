package com.w3llspring.fhpb.web.service.competition;

import com.w3llspring.fhpb.web.model.LadderConfig;

public interface GroupAdministrationOperations {

  LadderConfig regenInviteCode(Long ladderConfigId, Long requesterUserId);

  LadderConfig disableInviteCode(Long ladderConfigId, Long requesterUserId);

  LadderConfig syncInviteAvailability(
      LadderConfig cfg, Long requesterUserId, boolean invitesEnabled);

  LadderConfig joinByInvite(String inviteCode, Long userId);

  void leaveMember(Long configId, Long requesterUserId, Long membershipId);

  void banMember(Long configId, Long requesterUserId, Long membershipId);

  void removeSessionMember(Long configId, Long requesterUserId, Long membershipId);

  void unbanMember(Long configId, Long requesterUserId, Long membershipId);

  void requireActiveMember(Long configId, Long userId);

  void restorePendingDeletion(Long configId, Long requesterUserId);

  boolean canRestore(Long configId, Long userId);

  void promoteToAdmin(Long configId, Long requesterUserId, Long membershipId);

  void demoteFromAdmin(Long configId, Long requesterUserId, Long membershipId);

  LadderConfig updateTitle(Long configId, Long requesterUserId, String title);
}
