# Hướng Dẫn Tích Hợp Frontend - Đấu trường AI Kingdom Arena ⚔️⚙️

Tài liệu này hướng dẫn cách kết nối Frontend (React/Next.js) với các REST APIs và WebSocket của Backend đã được triển khai đầy đủ.

---

## 1. Thông Tin Địa Chỉ Kết Nối (Base URLs)

Khi chạy ở môi trường phát triển (Local Development), các cổng mặc định:
- **HTTP REST APIs**: `http://localhost:8080/api/battles`
- **WebSocket**: `ws://localhost:8080/ws-battle/{battleId}`
- *Lưu ý: Mọi API và WebSocket này đã được mở công khai (`permitAll()`), không yêu cầu Bearer Token bảo mật để tránh lỗi kết nối.*

---

## 2. Chi Tiết HTTP REST APIs

### 2.1. Khởi tạo Trận đấu mới (Create Battle)
- **Endpoint**: `POST /api/battles`
- **Headers**: `Content-Type: application/json`
- **Request Body JSON**:
```json
{
  "maxRound": 30,
  "kingdoms": [
    {
      "name": "Alpha Empire",
      "model": "gemini-1.5-flash",
      "apiKey": "AIzaSy..."
    },
    {
      "name": "Beta Dynasty",
      "model": "gpt-4o-mini",
      "apiKey": "sk-..."
    }
  ]
}
```
- **Response Body JSON** (Trả về trực tiếp đối tượng `BattleState` khớp với FE):
```json
{
  "battleId": "battle-a1b2c3d4",
  "maxRound": 30,
  "round": 0,
  "status": "WAITING",
  "kingdoms": [
    {
      "id": "k-1",
      "name": "Alpha Empire",
      "model": "gemini-1.5-flash",
      "population": 1000,
      "gold": 100,
      "supplies": 120,
      "energy": 80,
      "oil": 50,
      "infantry": 900,
      "tanks": 180,
      "aircraft": 45,
      "artillery": 135,
      "navy": 15,
      "drones": 75,
      "soldiers": 15,
      "tech": 1,
      "morale": 85,
      "score": 100,
      "scoreHistory": [100],
      "alive": true,
      "color": "#3b82f6"
    }
    // Danh sách các nước khác...
  ],
  "tiles": [
    {
      "id": "tile-1-1",
      "code": "B2",
      "x": 1,
      "y": 1,
      "type": "CAPITAL",
      "ownerKingdomId": "k-1",
      "level": 3,
      "defenseBonus": 5
    }
    // Danh sách 100 ô của lưới 10x10...
  ],
  "logs": [
    {
      "id": "uuid-string",
      "roundNumber": 0,
      "kingdomId": "system",
      "message": "Lobby phòng đấu đã được khởi tạo. Đang chờ bắt đầu...",
      "createdAt": "15:45:00"
    }
  ]
}
```

---

### 2.2. Lấy Trạng thái Trận đấu hiện tại (Get Battle State)
- **Endpoint**: `GET /api/battles/{battleId}`
- **Response Body JSON**: Trả về cấu trúc trạng thái trận đấu giống hệt như API tạo trận đấu phía trên.

---

### 2.3. Bắt đầu Mô phỏng Trận đấu (Start Battle)
- **Endpoint**: `POST /api/battles/{battleId}/start`
- **Response Body JSON**:
```json
{
  "success": true,
  "message": "Battle simulation started in the background."
}
```
*Lưu ý: API này sẽ lập tức trả về kết quả thành công, và Backend sẽ chạy vòng lặp mô phỏng bất đồng bộ (Asynchronous) ở chế độ nền để tính toán và liên tục gửi cập nhật về qua WebSocket.*

---

## 3. Tích Hợp Real-time Qua WebSocket

Frontend cần khởi tạo một kết nối WebSocket trực tiếp đến đường dẫn:
```javascript
const ws = new WebSocket(`ws://localhost:8080/ws-battle/${battleId}`);
```

Backend sẽ gửi các sự kiện (Events) dưới dạng chuỗi JSON. Dưới đây là các định dạng gói tin FE cần bắt:

### 3.1. Sự kiện `ROUND_START` (Đầu mỗi lượt mới)
Dùng để cập nhật vòng đấu hiện tại và các thông số tài nguyên được làm mới của các quốc gia.
```json
{
  "type": "ROUND_START",
  "payload": {
    "round": 1,
    "kingdoms": [
      { "id": "k-1", "gold": 110, "supplies": 130, "energy": 90, "oil": 55, "soldiers": 15, "morale": 85, "score": 102, "alive": true }
      // Các nước khác...
    ]
  }
}
```

### 3.2. Sự kiện `DISASTER_TRIGGERED` (Thiên tai / Dịch bệnh đột xuất)
Khi xảy ra sự kiện thiên tai, FE bắt gói này để cập nhật tài nguyên bị trừ và hiển thị hộp thoại cảnh báo Visual Novel.
```json
{
  "type": "DISASTER_TRIGGERED",
  "payload": {
    "effectType": "PLAGUE", // Hoặc "DISASTER"
    "targetKingdomId": "k-1",
    "soldiersLost": 4,
    "moraleLost": 20,
    "goldLost": 0,
    "suppliesLost": 0,
    "dialogue": {
      "type": "DISASTER",
      "senderId": "k-1",
      "senderName": "Alpha Empire",
      "senderColor": "#3b82f6",
      "senderModel": "gemini-1.5-flash",
      "message": "Nguy to! Đại dịch vương quốc 🦠 đang bùng phát dữ dội! Quân sĩ kiệt quệ!",
      "replyMessage": "Báo cáo bệ hạ, quân ta đã mất đi 30% lực lượng (trừ 4 binh sĩ)!"
    }
  }
}
```

### 3.3. Sự kiện `ACTION_SELECTED` (AI lựa chọn hành động)
Sự kiện này kích hoạt hiển thị hộp thoại hội thoại Visual Novel của vị vua quốc gia AI đó (bao gồm lời thoại tiếng Việt và phản hồi của quân sư).
```json
{
  "type": "ACTION_SELECTED",
  "payload": {
    "action": "ATTACK", // EXPAND, RECRUIT, ATTACK, DEFEND, RESEARCH, DIPLOMACY
    "dialogue": {
      "type": "ATTACK",
      "senderId": "k-1",
      "senderName": "Alpha Empire",
      "senderColor": "#3b82f6",
      "senderModel": "gemini-1.5-flash",
      "receiverId": "k-2",
      "message": "Tuyên chiến! Toàn quân tiến công đánh sập cứ điểm ô [B9] của Beta Dynasty!",
      "replyMessage": "Quân ta đã dàn trận tại biên giới ô [B9], sẵn sàng xung trận!",
      "targetTileCode": "B9"
    }
  }
}
```

### 3.4. Sự kiện `ACTION_EXECUTED` (Thực thi hành động)
Sự kiện này báo hiệu hành động đã hoàn tất. FE cần dựa vào đây để vẽ đường mũi tên tấn công (`attackLine`), cập nhật lại các ô đất trên bản đồ (`updatedTiles`) và tài nguyên cướp bóc (`lootedResources`).
```json
{
  "type": "ACTION_EXECUTED",
  "payload": {
    "action": "ATTACK",
    "kingdomId": "k-1",
    "success": true,
    "updatedTiles": [
      {
        "id": "tile-8-1",
        "code": "B9",
        "ownerKingdomId": "k-1",
        "level": 1,
        "defenseBonus": 1
      }
    ],
    "lootedResources": {
      "gold": 24,
      "supplies": 30,
      "targetKingdomId": "k-2"
    },
    "updatedKingdoms": [
      { "id": "k-1", "gold": 134, "supplies": 160, "soldiers": 11, "morale": 85, "score": 142, "alive": true },
      { "id": "k-2", "gold": 56, "supplies": 70, "soldiers": 9, "morale": 85, "score": 80, "alive": true }
    ],
    "attackLine": {
      "fromX": 1,
      "fromY": 1,
      "toX": 8,
      "toY": 1,
      "unitType": "TANK", // TANK, AIRCRAFT, DRONE
      "color": "#3b82f6"
    }
  }
}
```

---

## 4. Hướng dẫn sửa code gọi API ở Frontend (Ví dụ Axios/Fetch)

Trong tệp chứa API của Frontend (như `battleApi.ts`), hãy chỉnh sửa đường dẫn và gỡ bỏ việc tạo mock state để gọi trực tiếp tới Backend:

```typescript
export const battleApi = {
  createBattle: async (data: CreateBattleRequest): Promise<BattleState> => {
    // Gọi trực tiếp tới backend
    const response = await api.post('/battles', data);
    return response.data; // Backend trả trực tiếp BattleStateDto
  },

  getBattleState: async (battleId: string): Promise<BattleState> => {
    const response = await api.get(`/battles/${battleId}`);
    return response.data;
  },

  startBattle: async (battleId: string): Promise<{ success: boolean; message: string }> => {
    const response = await api.post(`/battles/${battleId}/start`);
    return response.data;
  },
};
```
