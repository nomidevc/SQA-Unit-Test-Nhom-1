'use client'

import { useState } from 'react'

export default function WarrantyCheckPage() {
  const [serialNumber, setSerialNumber] = useState('')

  const handleCheck = () => {
    alert('Tính năng tra cứu bảo hành đang được phát triển')
  }

  return (
    <div className="min-h-screen bg-gray-50 py-12">
      <div className="container mx-auto px-4 max-w-4xl">
        <h1 className="text-4xl font-bold text-gray-900 mb-8">Tra cứu bảo hành</h1>
        
        <div className="bg-white rounded-lg shadow-md p-8 space-y-6">
          <section>
            <h2 className="text-2xl font-semibold text-gray-800 mb-4">Kiểm tra thông tin bảo hành</h2>
            <p className="text-gray-600 mb-6">
              Nhập số serial/IMEI của sản phẩm để kiểm tra thông tin bảo hành
            </p>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Số Serial/IMEI
                </label>
                <input
                  type="text"
                  value={serialNumber}
                  onChange={(e) => setSerialNumber(e.target.value)}
                  placeholder="Nhập số serial hoặc IMEI"
                  className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                />
              </div>
              
              <button
                onClick={handleCheck}
                className="w-full bg-blue-600 text-white py-3 rounded-lg hover:bg-blue-700 transition-colors font-semibold"
              >
                Tra cứu
              </button>
            </div>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-gray-800 mb-4">Chính sách bảo hành</h2>
            <ul className="list-disc list-inside text-gray-600 space-y-2">
              <li>Bảo hành theo chính sách của nhà sản xuất</li>
              <li>Thời gian bảo hành: 12-24 tháng tùy sản phẩm</li>
              <li>Bảo hành tại các trung tâm bảo hành chính hãng</li>
              <li>Hỗ trợ đổi mới trong 7 ngày đầu nếu có lỗi từ NSX</li>
            </ul>
          </section>

          <section>
            <h2 className="text-2xl font-semibold text-gray-800 mb-4">Liên hệ hỗ trợ</h2>
            <div className="text-gray-600 space-y-2">
              <p><strong>Hotline:</strong> 1900.2091</p>
              <p><strong>Email:</strong> warranty@techworld.com</p>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
