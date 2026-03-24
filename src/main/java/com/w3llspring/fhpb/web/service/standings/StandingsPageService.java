package com.w3llspring.fhpb.web.service.standings;

import com.w3llspring.fhpb.web.service.LadderV2Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

@Service
public class StandingsPageService {

  private static final int MIN_PAGE_SIZE = 5;

  public int parsePage(String rawPage) {
    if (rawPage == null || rawPage.isBlank()) {
      return 0;
    }
    try {
      return Math.max(0, Integer.parseInt(rawPage));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  public int parsePage(Integer rawPage) {
    return rawPage == null ? 0 : Math.max(0, rawPage);
  }

  public int parsePageSize(String rawSize, int configuredDefaultPageSize, int maxPageSize) {
    if (rawSize == null || rawSize.isBlank()) {
      return resolveDefaultPageSize(configuredDefaultPageSize, maxPageSize);
    }
    try {
      return normalizePageSize(Integer.parseInt(rawSize), configuredDefaultPageSize, maxPageSize);
    } catch (NumberFormatException ex) {
      return resolveDefaultPageSize(configuredDefaultPageSize, maxPageSize);
    }
  }

  public int parsePageSize(Integer rawSize, int configuredDefaultPageSize, int maxPageSize) {
    return normalizePageSize(rawSize, configuredDefaultPageSize, maxPageSize);
  }

  public int resolvePageForCurrentUser(
      List<LadderV2Service.LadderRow> rows, Long currentUserId, int pageSize, int fallbackPage) {
    if (rows == null || rows.isEmpty() || currentUserId == null) {
      return fallbackPage;
    }
    for (int i = 0; i < rows.size(); i++) {
      LadderV2Service.LadderRow row = rows.get(i);
      if (row != null && Objects.equals(row.userId, currentUserId)) {
        return Math.max(0, i / Math.max(1, pageSize));
      }
    }
    return fallbackPage;
  }

  public List<LadderV2Service.LadderRow> filterToVisibleUsers(
      List<LadderV2Service.LadderRow> rows, Set<Long> visibleUserIds) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    if (visibleUserIds == null || visibleUserIds.isEmpty()) {
      return List.of();
    }
    return rows.stream()
        .filter(row -> row != null && row.userId != null && visibleUserIds.contains(row.userId))
        .toList();
  }

  public StandingsPage paginateRows(
      List<LadderV2Service.LadderRow> rows,
      int requestedPage,
      int requestedPageSize,
      int configuredDefaultPageSize,
      int maxPageSize) {
    List<LadderV2Service.LadderRow> safeRows = rows != null ? rows : List.of();
    int totalCount = safeRows.size();
    int pageSize = normalizePageSize(requestedPageSize, configuredDefaultPageSize, maxPageSize);
    int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
    int pageNumber = Math.max(0, Math.min(requestedPage, totalPages - 1));
    List<LadderV2Service.LadderRow> pagedRows;
    if (totalCount == 0) {
      pagedRows = List.of();
    } else {
      int fromIndex = pageNumber * pageSize;
      int toIndex = Math.min(totalCount, fromIndex + pageSize);
      pagedRows = copyRowsForPage(safeRows.subList(fromIndex, toIndex));
    }
    PaginationWindow pagination = buildPaginationWindow(pageNumber, totalPages);
    return new StandingsPage(
        pagedRows,
        pageNumber,
        pageSize,
        totalPages,
        totalCount,
        pagination.pageNumbers(),
        pagination.showFirstPage(),
        pagination.showLastPage(),
        pagination.showLeadingEllipsis(),
        pagination.showTrailingEllipsis(),
        pagination.jumpBackPage(),
        pagination.jumpForwardPage());
  }

  public void applyToModel(Model model, StandingsPage page) {
    if (model == null || page == null) {
      return;
    }
    model.addAttribute("ladderDisplay", page.rows());
    model.addAttribute("standingsPage", page.pageNumber());
    model.addAttribute("standingsPageSize", page.pageSize());
    model.addAttribute("standingsTotalPages", page.totalPages());
    model.addAttribute("standingsTotalCount", page.totalCount());
    model.addAttribute("standingsPageNumbers", page.pageNumbers());
    model.addAttribute("standingsShowFirstPage", page.showFirstPage());
    model.addAttribute("standingsShowLastPage", page.showLastPage());
    model.addAttribute("standingsShowLeadingEllipsis", page.showLeadingEllipsis());
    model.addAttribute("standingsShowTrailingEllipsis", page.showTrailingEllipsis());
    model.addAttribute("standingsJumpBackPage", page.jumpBackPage());
    model.addAttribute("standingsJumpForwardPage", page.jumpForwardPage());
  }

  private int normalizePageSize(
      Integer requestedSize, int configuredDefaultPageSize, int maxPageSize) {
    int defaultSize = resolveDefaultPageSize(configuredDefaultPageSize, maxPageSize);
    if (requestedSize == null) {
      return defaultSize;
    }
    return Math.max(MIN_PAGE_SIZE, Math.min(maxPageSize, requestedSize));
  }

  private int resolveDefaultPageSize(int configuredDefaultPageSize, int maxPageSize) {
    return Math.max(MIN_PAGE_SIZE, Math.min(maxPageSize, configuredDefaultPageSize));
  }

  private List<LadderV2Service.LadderRow> copyRowsForPage(
      List<LadderV2Service.LadderRow> sourceRows) {
    if (sourceRows == null || sourceRows.isEmpty()) {
      return List.of();
    }
    List<LadderV2Service.LadderRow> copiedRows = new ArrayList<>(sourceRows.size());
    String currentBandName = null;
    for (LadderV2Service.LadderRow source : sourceRows) {
      if (source == null) {
        continue;
      }
      LadderV2Service.LadderRow row = new LadderV2Service.LadderRow();
      row.rank = source.rank;
      row.displayName = source.displayName;
      row.userPublicCode = source.userPublicCode;
      row.points = source.points;
      row.bandIndex = source.bandIndex;
      row.bandLabel = source.bandLabel;
      row.bandCssClass = source.bandCssClass;
      row.bandName = source.bandName;
      row.momentum = source.momentum;
      row.userId = source.userId;
      row.competitionSafeDisplayNameActive = source.competitionSafeDisplayNameActive;
      row.badgeViews = source.badgeViews != null ? List.copyOf(source.badgeViews) : List.of();
      row.showBandHeader = !Objects.equals(currentBandName, source.bandName);
      currentBandName = source.bandName;
      copiedRows.add(row);
    }
    return copiedRows;
  }

  private PaginationWindow buildPaginationWindow(int currentPage, int totalPages) {
    if (totalPages <= 1) {
      return new PaginationWindow(Collections.emptyList(), false, false, false, false, null, null);
    }
    if (totalPages <= 7) {
      List<Integer> pageNumbers = new ArrayList<>();
      for (int i = 0; i < totalPages; i++) {
        pageNumbers.add(i);
      }
      return new PaginationWindow(pageNumbers, false, false, false, false, null, null);
    }

    int interiorStart;
    int interiorEnd;
    if (currentPage <= 2) {
      interiorStart = 1;
      interiorEnd = Math.min(totalPages - 2, 3);
    } else if (currentPage >= totalPages - 3) {
      interiorEnd = totalPages - 2;
      interiorStart = Math.max(1, totalPages - 4);
    } else {
      interiorStart = currentPage - 1;
      interiorEnd = currentPage + 1;
    }

    List<Integer> pageNumbers = new ArrayList<>();
    for (int i = interiorStart; i <= interiorEnd; i++) {
      pageNumbers.add(i);
    }

    boolean showLeadingEllipsis = interiorStart > 1;
    boolean showTrailingEllipsis = interiorEnd < totalPages - 2;
    Integer jumpBackPage = (showLeadingEllipsis && currentPage - 5 > 0) ? currentPage - 5 : null;
    Integer jumpForwardPage =
        (showTrailingEllipsis && currentPage + 5 < totalPages - 1) ? currentPage + 5 : null;

    return new PaginationWindow(
        pageNumbers,
        true,
        true,
        showLeadingEllipsis,
        showTrailingEllipsis,
        jumpBackPage,
        jumpForwardPage);
  }

  public record StandingsPage(
      List<LadderV2Service.LadderRow> rows,
      int pageNumber,
      int pageSize,
      int totalPages,
      int totalCount,
      List<Integer> pageNumbers,
      boolean showFirstPage,
      boolean showLastPage,
      boolean showLeadingEllipsis,
      boolean showTrailingEllipsis,
      Integer jumpBackPage,
      Integer jumpForwardPage) {}

  private record PaginationWindow(
      List<Integer> pageNumbers,
      boolean showFirstPage,
      boolean showLastPage,
      boolean showLeadingEllipsis,
      boolean showTrailingEllipsis,
      Integer jumpBackPage,
      Integer jumpForwardPage) {}
}
