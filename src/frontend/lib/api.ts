import axios from 'axios'

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api'

const getStoredAuthToken = () => {
  if (typeof window === 'undefined') {
    return null
  }

  const directToken = localStorage.getItem('auth_token') || localStorage.getItem('token')
  if (directToken) {
    return directToken
  }

  try {
    const authStorage = localStorage.getItem('auth-storage')
    if (!authStorage) {
      return null
    }

    const parsedStorage = JSON.parse(authStorage)
    return parsedStorage?.state?.token || null
  } catch {
    return null
  }
}

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

// Add token to requests if available
apiClient.interceptors.request.use((config) => {
  const token = getStoredAuthToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

interface ApiResponse<T> {
  success: boolean
  data?: T
  message?: string
  error?: string
}

// Auth API
export const authApi = {
  login: async (data: { email: string; password: string }): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/auth/login', data)
      const token = response.data?.data?.token || response.data?.token
      if (token) {
        localStorage.setItem('auth_token', token)
        localStorage.setItem('token', token)
      }
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Đăng nhập thất bại')
    }
  },

  register: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/auth/register', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Đăng ký thất bại')
    }
  },

  sendOtp: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/auth/register/send-otp', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Gửi OTP thất bại')
    }
  },

  verifyOtp: async (data: { email: string; otpCode: string }): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/auth/register/verify-otp', data)
      const token = response.data?.data?.token || response.data?.token
      if (token) {
        localStorage.setItem('auth_token', token)
        localStorage.setItem('token', token)
      }
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Xác thực OTP thất bại')
    }
  },

  logout: async (): Promise<void> => {
    localStorage.removeItem('auth_token')
  },

  getCurrentUser: async (): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get('/auth/me')
      return response.data
    } catch (error: any) {
      return {
        success: false,
        error: 'Không thể lấy thông tin người dùng'
      }
    }
  },

  approveEmployee: async (data: { userId: string; status: 'APPROVED' | 'REJECTED'; reason?: string }): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/employee-registration/approve/{id}', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi duyệt nhân viên')
    }
  },

  getPendingEmployees: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/employee-registration/list')
      return {
        success: true,
        data: response.data || [],
      }
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  registerEmployee: async (data: {
    fullName: string
    email: string
    phone: string
    address?: string
    position: string
    note?: string
  }): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/employee-registration/apply', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi đăng ký nhân viên')
    }
  },
}

// Category API
export const categoryApi = {
  getAll: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/categories')
      console.log('Category API raw response:', response)
      
      // Kiểm tra nếu response.data là ApiResponse
      if (response.data && response.data.data) {
        return {
          success: true,
          data: Array.isArray(response.data.data) ? response.data.data : [],
        }
      }
      
      // Nếu response.data là array trực tiếp
      return {
        success: true,
        data: Array.isArray(response.data) ? response.data : [],
      }
    } catch (error: any) {
      console.error('Category API error:', error)
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  getById: async (id: string | number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/categories/${id}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy thông tin danh mục')
    }
  },

  getActiveCategories: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/categories/active')
      console.log('Active categories response:', response)
      
      if (response.data && response.data.data) {
        return {
          success: true,
          data: Array.isArray(response.data.data) ? response.data.data : [],
        }
      }
      
      return {
        success: true,
        data: Array.isArray(response.data) ? response.data : [],
      }
    } catch (error: any) {
      console.error('Active categories error:', error)
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  getCategoriesTree: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/categories/tree')
      console.log('Categories tree response:', response)
      
      if (response.data && response.data.data) {
        return {
          success: true,
          data: Array.isArray(response.data.data) ? response.data.data : [],
        }
      }
      
      return {
        success: true,
        data: Array.isArray(response.data) ? response.data : [],
      }
    } catch (error: any) {
      console.error('Categories tree error:', error)
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  create: async (data: any): Promise<ApiResponse<any>> => {
    try {
      console.log('🔑 Creating category with token:', sessionStorage.getItem('auth_token')?.substring(0, 20) + '...')
      const response = await apiClient.post('/categories', data)
      return response.data
    } catch (error: any) {
      console.error('❌ Category create error:', error.response?.data)
      console.error('❌ Status:', error.response?.status)
      console.error('❌ Headers:', error.response?.headers)
      throw new Error(error.response?.data?.message || error.response?.data?.error || 'Lỗi khi tạo danh mục')
    }
  },

  update: async (id: number, data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/categories/${id}`, data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi cập nhật danh mục')
    }
  },

  delete: async (id: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.delete(`/categories/${id}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi xóa danh mục')
    }
  },
}

// Product API
export const productApi = {
  getAll: async (params?: any): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/products', { params })
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  create: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/products', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tạo sản phẩm')
    }
  },

  getById: async (id: string | number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/products/${id}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy thông tin sản phẩm')
    }
  },

  search: async (query: string): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/products/search', { params: { q: query } })
      return {
        success: true,
        data: response.data || [],
      }
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  // Warehouse product management for publishing
  getWarehouseProductsForPublish: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/products/warehouse/list')
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  createProductFromWarehouse: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/products/warehouse/publish', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi đăng bán sản phẩm')
    }
  },

  update: async (productId: number, data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/products/${productId}`, data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi cập nhật sản phẩm')
    }
  },

  delete: async (productId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.delete(`/products/${productId}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi xóa sản phẩm')
    }
  },

  updatePublishedProduct: async (productId: number, data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/products/warehouse/publish/${productId}`, data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi cập nhật sản phẩm')
    }
  },

  unpublishProduct: async (productId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.delete(`/products/warehouse/unpublish/${productId}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi gỡ sản phẩm')
    }
  },

  // Product Images Management
  getProductImages: async (productId: number): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get(`/products/${productId}/images`)
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  addProductImage: async (productId: number, imageUrl: string, isPrimary: boolean = false): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post(`/products/${productId}/images`, { imageUrl, isPrimary })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi thêm ảnh')
    }
  },

  setPrimaryImage: async (productId: number, imageId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/products/${productId}/images/${imageId}/primary`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi đặt ảnh chính')
    }
  },

  deleteProductImage: async (imageId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.delete(`/products/images/${imageId}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi xóa ảnh')
    }
  },

  reorderProductImages: async (productId: number, imageIds: number[]): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/products/${productId}/images/reorder`, { imageIds })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi sắp xếp ảnh')
    }
  },

  toggleActive: async (productId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/products/${productId}/toggle-active`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi thay đổi trạng thái')
    }
  },
}

export const fileApi = {
  uploadImage: async (file: File): Promise<ApiResponse<string>> => {
    const formData = new FormData()
    formData.append('file', file)

    try {
      const response = await apiClient.post('/files/upload', formData, {
        headers: {
          'Content-Type': undefined,
        },
      })
      return response.data
    } catch (cloudinaryError: any) {
      try {
        const response = await apiClient.post('/files/upload-local', formData, {
          headers: {
            'Content-Type': undefined,
          },
        })
        return response.data
      } catch (localError: any) {
        throw new Error(
          localError.response?.data?.message ||
            cloudinaryError.response?.data?.message ||
            'Lỗi khi upload ảnh'
        )
      }
    }
  },
}

// Order API
export const orderApi = {
  create: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/orders', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tạo đơn hàng')
    }
  },

  getAll: async (params?: any): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/orders', { params })
      // Backend trả về {success, message, data}, nên cần lấy response.data.data
      return {
        success: true,
        data: response.data?.data || response.data || [],
      }
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  getById: async (id: string | number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/orders/${id}`)
      // Backend trả về {success, message, data}, nên cần lấy response.data.data
      return {
        success: true,
        data: response.data?.data || response.data,
      }
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy thông tin đơn hàng')
    }
  },

  getByCode: async (orderCode: string): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/orders/code/${orderCode}`)
      // Backend trả về {success, message, data}
      return {
        success: true,
        data: response.data?.data || response.data,
      }
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy thông tin đơn hàng')
    }
  },

  getShippingStatus: async (orderId: string | number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/orders/${orderId}/shipping-status`)
      return {
        success: true,
        data: response.data?.data || response.data,
      }
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy thông tin vận chuyển')
    }
  },

  cancelOrder: async (orderId: string | number, reason?: string): Promise<ApiResponse<any>> => {
    try {
      const params = reason ? { reason } : {}
      const response = await apiClient.put(`/orders/${orderId}/cancel`, null, { params })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi hủy đơn hàng')
    }
  },

  confirmReceived: async (orderId: string | number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/orders/${orderId}/confirm-received`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi xác nhận nhận hàng')
    }
  },
}

// Admin Order API
export const adminOrderApi = {
  getAll: async (status?: string): Promise<ApiResponse<any[]>> => {
    try {
      const params = status ? { status } : {}
      const response = await apiClient.get('/admin/orders', { params })
      return {
        success: true,
        data: response.data?.data || response.data || [],
      }
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tải đơn hàng')
    }
  },

  getById: async (orderId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/admin/orders/${orderId}`)
      return {
        success: true,
        data: response.data?.data || response.data,
      }
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy thông tin đơn hàng')
    }
  },

  getShippingStatus: async (orderId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/admin/orders/${orderId}/shipping-status`)
      return {
        success: true,
        data: response.data?.data || response.data,
      }
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy thông tin vận chuyển')
    }
  },

  confirmOrder: async (orderId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/admin/orders/${orderId}/confirm`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi xác nhận đơn hàng')
    }
  },

  updateStatus: async (orderId: number, status: string): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/admin/orders/${orderId}/status`, null, {
        params: { status }
      })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi cập nhật trạng thái')
    }
  },

  // DEPRECATED: Không dùng nữa - chỉ shipper mới được cập nhật trạng thái giao hàng
  // markAsShipping và markAsDelivered đã được chuyển sang ShipperAssignment API

  markAsDelivered: async (orderId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/admin/orders/${orderId}/delivered`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi cập nhật trạng thái')
    }
  },

  // Cập nhật từ READY_TO_SHIP sang SHIPPING (CHỈ endpoint này được dùng)
  // Không cho phép cập nhật từ CONFIRMED hoặc các trạng thái khác
  markShippingFromReady: async (orderId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/admin/orders/${orderId}/mark-shipping-from-ready`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi cập nhật trạng thái')
    }
  },
}

// Contact API
export const contactApi = {
  sendMessage: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/contact/messages', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi gửi tin nhắn')
    }
  },
}

// Customer API
export const customerApi = {
  getProfile: async (): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get('/customer/profile')
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tải thông tin khách hàng')
    }
  },
  
  updateProfile: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put('/customer/profile', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi cập nhật thông tin')
    }
  },

  // Lấy danh sách tất cả khách hàng (cho nhân viên/admin)
  getAll: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/customer/all')
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  // Lấy đơn hàng của 1 khách hàng (cho nhân viên/admin)
  getOrdersByCustomerId: async (customerId: number): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get(`/orders/customer/${customerId}`)
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },
}

// Inventory API
export const inventoryApi = {
  createPurchaseOrder: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/inventory/create_pchaseOrder', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tạo đơn nhập hàng')
    }
  },

  getSuppliers: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/inventory/suppliers')
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tải danh sách nhà cung cấp')
    }
  },

  createSupplier: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/inventory/suppliers', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tạo nhà cung cấp')
    }
  },

  getProductsBySupplier: async (supplierId: number): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get(`/inventory/supplier/${supplierId}/products`)
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  importStock: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/inventory/import', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi nhập hàng')
    }
  },

  createExportOrder: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/inventory/create', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tạo đơn xuất hàng')
    }
  },

  getPurchaseOrders: async (status?: string): Promise<ApiResponse<any[]>> => {
    try {
      const params = status ? { status } : {}
      const response = await apiClient.get('/inventory/purchase-orders', { params })
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  getExportOrders: async (status?: string): Promise<ApiResponse<any[]>> => {
    try {
      const params = status ? { status } : {}
      const response = await apiClient.get('/inventory/export-orders', { params })
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  getPurchaseOrderDetail: async (id: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/inventory/purchase-orders/${id}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tải chi tiết phiếu nhập')
    }
  },

  getExportOrderDetail: async (id: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/inventory/export-orders/${id}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tải chi tiết phiếu xuất')
    }
  },

  completePurchaseOrder: async (data: any): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/inventory/import', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi hoàn tất đơn nhập')
    }
  },

  cancelTransaction: async (id: number, type: string): Promise<ApiResponse<any>> => {
    try {
      const endpoint = type === 'IMPORT' ? 'purchase-orders' : 'export-orders'
      const response = await apiClient.put(`/inventory/${endpoint}/${id}/cancel`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi hủy phiếu')
    }
  },

  getStocks: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/inventory/stock')
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },
}

// Cart API
export const cartApi = {
  getCart: async (): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get('/cart')
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: null,
        error: error.message,
      }
    }
  },

  addToCart: async (productId: number, quantity: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/cart/items', { productId, quantity })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi thêm vào giỏ hàng')
    }
  },

  updateCartItem: async (itemId: number, quantity: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/cart/items/${itemId}`, { quantity })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi cập nhật giỏ hàng')
    }
  },

  removeCartItem: async (itemId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.delete(`/cart/items/${itemId}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi xóa sản phẩm')
    }
  },

  clearCart: async (): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.delete('/cart')
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi xóa giỏ hàng')
    }
  },
}

// Review API
export const reviewApi = {
  // Tạo đánh giá mới hoặc comment
  create: async (data: {
    productId: number
    orderId?: number | null
    rating?: number | null
    comment: string
  }): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/reviews', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tạo đánh giá')
    }
  },

  // Tạo đánh giá mới (legacy - giữ để tương thích)
  createReview: async (data: {
    productId: number
    orderId: number
    rating: number
    comment?: string
  }): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/reviews', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi tạo đánh giá')
    }
  },

  // Lấy đánh giá theo sản phẩm
  getByProduct: async (productId: number): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get(`/reviews/product/${productId}`)
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  // Lấy thống kê đánh giá sản phẩm
  getProductSummary: async (productId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/reviews/product/${productId}/summary`)
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: null,
        error: error.message,
      }
    }
  },

  // Lấy đánh giá của khách hàng
  getMyReviews: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/reviews/my-reviews')
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  // Kiểm tra có thể đánh giá không
  checkCanReview: async (orderId: number, productId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get('/reviews/can-review', {
        params: { orderId, productId }
      })
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: { canReview: false },
        error: error.message,
      }
    }
  },

  // Admin xóa review/comment
  delete: async (reviewId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.delete(`/reviews/${reviewId}`)
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: null,
        error: error.response?.data?.message || 'Lỗi khi xóa bình luận',
      }
    }
  },
}

// Shipper Assignment API
export const shipperApi = {
  // Lấy danh sách đơn hàng có thể nhận
  getAvailableOrders: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/shipper-assignments/available')
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  // Shipper nhận đơn
  claimOrder: async (orderId: number, shipperId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/shipper-assignments/claim', null, {
        params: { orderId, shipperId }
      })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi nhận đơn')
    }
  },

  // Lấy danh sách đơn đã nhận của shipper
  getMyOrders: async (shipperId: number): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/shipper-assignments/my-orders', {
        params: { shipperId }
      })
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  // Lấy danh sách đơn đang giao của shipper
  getMyActiveOrders: async (shipperId: number): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/shipper-assignments/my-active-orders', {
        params: { shipperId }
      })
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  // Shipper bắt đầu giao hàng (đã lấy hàng từ kho)
  startDelivery: async (assignmentId: number, shipperId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/shipper-assignments/${assignmentId}/start-delivery`, null, {
        params: { shipperId }
      })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi bắt đầu giao hàng')
    }
  },

  // Shipper xác nhận giao thành công
  confirmDelivery: async (assignmentId: number, shipperId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/shipper-assignments/${assignmentId}/deliver`, null, {
        params: { shipperId }
      })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi xác nhận giao hàng')
    }
  },

  // Shipper báo giao thất bại
  reportFailure: async (assignmentId: number, shipperId: number, reason: string): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/shipper-assignments/${assignmentId}/fail`, null, {
        params: { shipperId, reason }
      })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi báo thất bại')
    }
  },

  // Shipper hủy nhận đơn
  cancelClaim: async (assignmentId: number, shipperId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.put(`/shipper-assignments/${assignmentId}/cancel`, null, {
        params: { shipperId }
      })
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi hủy nhận đơn')
    }
  },

  // Lấy chi tiết assignment
  getAssignmentDetail: async (assignmentId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/shipper-assignments/${assignmentId}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy chi tiết')
    }
  },

  // Lấy tất cả assignments (cho admin/nhân viên xem)
  getAllAssignments: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/shipper-assignments/all')
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: [],
        error: error.message,
      }
    }
  },

  // Lấy assignment theo orderId
  getAssignmentByOrder: async (orderId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/shipper-assignments/by-order/${orderId}`)
      return response.data
    } catch (error: any) {
      return {
        success: false,
        data: null,
        error: error.message,
      }
    }
  },
}

export default apiClient


// Supplier Payable API (Công nợ nhà cung cấp)
export const payableApi = {
  // Lấy tất cả công nợ
  getAll: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/accounting/payables')
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy danh sách công nợ')
    }
  },

  // Lấy công nợ theo ID
  getById: async (id: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/accounting/payables/${id}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy thông tin công nợ')
    }
  },

  // Lấy công nợ theo nhà cung cấp
  getBySupplier: async (supplierId: number): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/accounting/payables/supplier/${supplierId}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy công nợ theo NCC')
    }
  },

  // Lấy công nợ quá hạn
  getOverdue: async (): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get('/accounting/payables/overdue')
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy công nợ quá hạn')
    }
  },

  // Lấy công nợ sắp đến hạn
  getUpcoming: async (days: number = 7): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get(`/accounting/payables/upcoming?days=${days}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy công nợ sắp đến hạn')
    }
  },

  // Thanh toán công nợ
  makePayment: async (data: {
    payableId: number
    amount: number
    paymentDate: string
    paymentMethod: 'CASH' | 'BANK_TRANSFER' | 'CHECK'
    referenceNumber?: string
    note?: string
  }): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.post('/accounting/payables/payments', data)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi thanh toán')
    }
  },

  // Lấy lịch sử thanh toán
  getPaymentHistory: async (payableId: number): Promise<ApiResponse<any[]>> => {
    try {
      const response = await apiClient.get(`/accounting/payables/${payableId}/payments`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy lịch sử thanh toán')
    }
  },

  // Thống kê công nợ
  getStats: async (): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get('/accounting/payables/stats')
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy thống kê')
    }
  },

  // Báo cáo công nợ
  getReport: async (startDate: string, endDate: string): Promise<ApiResponse<any>> => {
    try {
      const response = await apiClient.get(`/accounting/payables/report?startDate=${startDate}&endDate=${endDate}`)
      return response.data
    } catch (error: any) {
      throw new Error(error.response?.data?.message || 'Lỗi khi lấy báo cáo')
    }
  }
}
