export interface Review {
  id: string;
  buyerName: string;
  rating: number;
  comment: string | null;
  productId: string | null;
  createdAt: string;
}

export interface ReviewInput {
  rating: number;
  comment?: string;
}
