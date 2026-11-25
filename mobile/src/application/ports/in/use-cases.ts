import type { ApiStatus } from '../../../domain/model/ApiStatus';

// Puerto de entrada que consume la capa de presentación
export interface UseCases {
  checkApiHealth(): Promise<ApiStatus>;
}
