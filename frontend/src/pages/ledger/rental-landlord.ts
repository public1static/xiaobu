export interface RentalLandlordDraft {
  name: string;
  contactId: string | null;
  distinction: string;
  sameName?: boolean;
  checking?: boolean;
  ambiguous?: boolean;
}

export function validateRentalLandlord(draft: RentalLandlordDraft) {
  if (!draft.name.trim()) return '请输入出租方姓名或机构全称。';
  if (draft.checking) return '请先完成联系人查询。';
  if (draft.ambiguous) return '请选择一位同名联系人，或新增同名。';
  if (draft.sameName && !draft.distinction.trim()) return '请填写用于区分的称呼或关系。';
  return '';
}
