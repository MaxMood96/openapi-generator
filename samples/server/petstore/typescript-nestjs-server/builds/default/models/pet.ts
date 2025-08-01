import { Category } from './category';
import { Tag } from './tag';


/**
 * A pet for sale in the pet store
 */
export interface Pet { 
  id?: number;
  category?: Category;
  name: string;
  photoUrls: Array<string>;
  tags?: Array<Tag>;
  /**
   * pet status in the store
   * @deprecated
   */
  status?: Pet.StatusEnum;
}
export namespace Pet {
  export const StatusEnum = {
    Available: 'available',
    Pending: 'pending',
    Sold: 'sold'
  } as const;
  export type StatusEnum = typeof StatusEnum[keyof typeof StatusEnum];
}


