package com.w3llspring.fhpb.web.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PaginationWindowSupport {

  private PaginationWindowSupport() {}

  public static PaginationWindow buildWindow(int currentPage, int totalPages) {
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

  public static <T> PageSlice<T> slice(List<T> sourceItems, int requestedPage, int requestedSize) {
    List<T> safeItems = sourceItems != null ? sourceItems : List.of();
    int pageSize = Math.max(1, requestedSize);
    int totalCount = safeItems.size();
    int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / pageSize);
    int pageNumber = Math.max(0, Math.min(requestedPage, totalPages - 1));
    if (totalCount == 0) {
      return new PageSlice<>(List.of(), pageNumber, pageSize, totalPages, 0);
    }

    int fromIndex = pageNumber * pageSize;
    int toIndex = Math.min(totalCount, fromIndex + pageSize);
    return new PageSlice<>(
        List.copyOf(safeItems.subList(fromIndex, toIndex)),
        pageNumber,
        pageSize,
        totalPages,
        totalCount);
  }

  public record PaginationWindow(
      List<Integer> pageNumbers,
      boolean showFirstPage,
      boolean showLastPage,
      boolean showLeadingEllipsis,
      boolean showTrailingEllipsis,
      Integer jumpBackPage,
      Integer jumpForwardPage) {}

  public record PageSlice<T>(
      List<T> items, int pageNumber, int pageSize, int totalPages, int totalCount) {}
}
