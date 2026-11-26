package com.xiaobu.gift.interfaces;

import com.xiaobu.gift.application.ChangeGiftBookArchiveCommand;
import com.xiaobu.gift.application.CreateGiftBookCommand;
import com.xiaobu.gift.application.CreateGiftContactCommand;
import com.xiaobu.gift.application.GiftActivityPageResult;
import com.xiaobu.gift.application.GiftActivityResult;
import com.xiaobu.gift.application.GiftBookResult;
import com.xiaobu.gift.application.GiftContactResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface GiftDirectoryWebMapper {
  CreateGiftBookCommand toCommand(
      GiftDirectoryController.CreateGiftBookRequest request, String ledgerId, String actorId);

  ChangeGiftBookArchiveCommand toCommand(
      GiftDirectoryController.ChangeGiftBookArchiveRequest request,
      String ledgerId,
      String actorId,
      String bookId);

  CreateGiftContactCommand toCommand(
      GiftDirectoryController.CreateGiftContactRequest request, String ledgerId, String actorId);

  GiftBookResponse toResponse(GiftBookResult result);

  GiftContactResponse toResponse(GiftContactResult result);

  GiftActivityResponse toResponse(GiftActivityResult result);

  GiftActivityPageResponse toResponse(GiftActivityPageResult result);
}
