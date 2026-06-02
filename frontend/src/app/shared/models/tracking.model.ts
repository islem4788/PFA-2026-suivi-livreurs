export type DrivingState = 'NORMAL' | 'RISKY' | 'AGGRESSIVE';
export type LivreurStatus = 'ACTIVE' | 'IDLE' | 'OFFLINE' | 'ALERT';

export interface LivreurPosition {
  livreurId: string;
  firstName: string;
  lastName: string;
  latitude: number;
  longitude: number;
  speed: number;
  heading?: number;
  accuracy?: number;
  drivingState: DrivingState;
  status: LivreurStatus;
  lastUpdate: string;
  accX?: number | null;
  accY?: number | null;
  accZ?: number | null;
  gyrX?: number | null;
  gyrY?: number | null;
  gyrZ?: number | null;
}

export interface Alert {
  id: string;
  livreurId: string;
  livreurName: string;
  type: AlertType;
  message: string;
  latitude: number;
  longitude: number;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  createdAt: string;
  resolved: boolean;
}

export type AlertType =
  | 'IMU_ANOMALY'
  | 'STATIONARY_TIMEOUT'
  | 'AGGRESSIVE_DRIVING'
  | 'TRAFFIC_REPORT'
  | 'ACCIDENT'
  | 'CLIENT_COMPLAINT';

export interface TrafficReport {
  livreurId: string;
  type: 'JAM' | 'ACCIDENT' | 'ROAD_BLOCKED' | 'CONSTRUCTION';
  description: string;
  latitude: number;
  longitude: number;
}

export interface ClientFeedback {
  packageId: string;
  clientName: string;
  clientAddress: string;
  comment: string;
  rating?: number;
}

export interface ClientFeedbackResponse {
  id: string;
  packageId: string;
  clientName: string;
  clientAddress: string;
  comment: string;
  rating?: number;
  createdAt: string;
}
