package com.w3llspring.fhpb.web.service.user;

import com.w3llspring.fhpb.web.db.UserRepository;
import com.w3llspring.fhpb.web.model.User;
import com.w3llspring.fhpb.web.model.UserCourtName;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Allocates readable member codes and retries entire creations after a concurrent collision. */
@Service
public class UserCreationService {
  private static final int MAX_ATTEMPTS = 24;
  private final UserRepository users;
  private final TransactionTemplate transaction;
  private final Supplier<String> codes;

  @Autowired
  public UserCreationService(UserRepository users, PlatformTransactionManager transactions) {
    this(users, transactions, UserPublicCodeGenerator::nextCode);
  }

  UserCreationService(
      UserRepository users, PlatformTransactionManager transactions, Supplier<String> codes) {
    this.users = users;
    this.codes = codes;
    this.transaction = new TransactionTemplate(transactions);
    this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** The callback must build fresh entities each time, including any dependent session records. */
  public <T> T create(Function<String, T> creation) {
    for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
      String code = codes.get();
      if (codeExists(code)) continue;
      try {
        return transaction.execute(status -> Objects.requireNonNull(creation.apply(code)));
      } catch (DataIntegrityViolationException ex) {
        // The failed transaction has rolled back. A fresh read handles the race between
        // checking availability and inserting, without parsing database-specific error text.
        if (!codeExists(code)) throw ex;
      }
    }
    throw new IllegalStateException("Unable to assign a member ID. Please try again.");
  }

  public User register(User registration) {
    if (registration.getId() != null)
      throw new IllegalArgumentException("A new account is required.");
    return create(
        code -> {
          // Copy only signup data; never reuse entities from a failed persistence context.
          User user = new User();
          user.setPublicCode(code);
          user.setEmail(registration.getEmail());
          user.setPassword(registration.getPassword());
          user.setNickName(registration.getNickName());
          user.setTimeZone(registration.getTimeZone());
          user.setRegisteredAt(registration.getRegisteredAt());
          user.setAcknowledgedTermsAt(registration.getAcknowledgedTermsAt());
          user.setMaxOwnedLadders(registration.getMaxOwnedLadders());
          for (UserCourtName original : registration.getCourtNames()) {
            UserCourtName alias = new UserCourtName();
            alias.setUser(user);
            alias.setAlias(original.getAlias());
            user.getCourtNames().add(alias);
          }
          return users.saveAndFlush(user);
        });
  }

  private boolean codeExists(String code) {
    return Boolean.TRUE.equals(transaction.execute(status -> users.existsByPublicCode(code)));
  }
}
