# IoT-Based Compost Accelerator System

A React-based UI for monitoring and controlling an IoT compost accelerator with AI prediction capabilities.

## Features

- User authentication (Login/Register)
- Dashboard with **real-time sensor monitoring** from IoT devices
- Device control panel (Fan, Spray, Mixing)
- AI prediction insights
- Logs and history overview
- Real sensor data storage in database

## Real Sensor Integration

This system is designed to work with **actual IoT sensors** (temperature, humidity, moisture, gas sensors) connected via ESP32/Arduino.

**For detailed setup instructions, see:** [REAL_SENSOR_INTEGRATION.md](./REAL_SENSOR_INTEGRATION.md)

### Quick Start for Sensors
1. Your IoT device posts sensor readings to: `POST /api/sensor-readings`
2. Data is automatically stored in the database
3. Dashboard fetches latest readings every 30 seconds
4. Real-time data appears in the monitoring dashboard

### Example ESP32 Code
See [REAL_SENSOR_INTEGRATION.md](./REAL_SENSOR_INTEGRATION.md#arduino--esp32-implementation-example) for complete Arduino/ESP32 implementation with WiFi integration.

## Getting Started

### Prerequisites

- [Node.js](https://nodejs.org/) (LTS version recommended)

### Installation

1. Clone or download this project to your local machine.

2. Open a terminal in the project folder (`c:\xampp\htdocs\CompostAcceleratorSystem`).

3. Install dependencies:
   ```powershell
   npm.cmd install
   ```

### Running the Application

1. Start the development server:
   ```powershell
   npm.cmd run dev
   ```

2. Open your browser and navigate to:
   ```
   http://localhost:5173
   ```

### Building for Production

```powershell
npm.cmd run build
```

This creates a `dist` folder with optimized files.

## Troubleshooting

### PowerShell Execution Policy Error

If you see errors like "running scripts is disabled on this system", use `npm.cmd` instead of `npm` for all commands:

- `npm.cmd install`
- `npm.cmd run dev`
- `npm.cmd run build`

Alternatively, you can change PowerShell's execution policy (requires admin privileges):

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### Port Already in Use

If port 5173 is busy, Vite will suggest an alternative port. Check the terminal output for the correct URL.

## Project Structure

```
src/
├── pages/
│   ├── Login.jsx
│   ├── Register.jsx
│   └── Dashboard.jsx
├── App.jsx
├── main.jsx
└── styles.css
```

## Technologies Used

- React 18
- React Router DOM
- Vite (build tool)
- CSS (styling)
