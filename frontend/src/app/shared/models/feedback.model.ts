export interface ClientFeedbackEntry {
  id: string;
  packageId: string;
  clientName: string;
  clientAddress: string;
  comment: string;
  rating?: number;
  createdAt: string;
}
