'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import Image from 'next/image'
import { useParams, useRouter } from 'next/navigation'
import { FiImage, FiSave, FiUpload, FiX } from 'react-icons/fi'
import toast from 'react-hot-toast'
import { useAuthStore } from '@/store/authStore'
import { categoryApi, fileApi, productApi } from '@/lib/api'

type ProductFormState = {
  name: string
  sku: string
  description: string
  price: string
  originalPrice: string
  category: string
  brand: string
  stock: string
  status: 'ACTIVE' | 'INACTIVE'
  specifications: Record<string, string>
}

type CategoryOption = {
  id: number
  name: string
}

type EditableImage = {
  id?: number
  file?: File
  previewUrl: string
  isExisting: boolean
}

const canManageProducts = (user: ReturnType<typeof useAuthStore.getState>['user']) => {
  if (!user) {
    return false
  }

  return (
    user.role === 'ADMIN' ||
    (user.role === 'EMPLOYEE' && (user.position === 'PRODUCT_MANAGER' || user.employee?.position === 'PRODUCT_MANAGER'))
  )
}

export default function EditProductPage() {
  const params = useParams<{ id: string }>()
  const router = useRouter()
  const { user, isAuthenticated } = useAuthStore()

  const [formData, setFormData] = useState<ProductFormState>({
    name: '',
    sku: '',
    description: '',
    price: '',
    originalPrice: '',
    category: '',
    brand: '',
    stock: '',
    status: 'ACTIVE',
    specifications: {
      screen: '',
      cpu: '',
      ram: '',
      storage: '',
      battery: '',
      camera: '',
    },
  })
  const [categories, setCategories] = useState<CategoryOption[]>([])
  const [images, setImages] = useState<EditableImage[]>([])
  const [existingImageIds, setExistingImageIds] = useState<number[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [isLoadingCategories, setIsLoadingCategories] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const imagePreviewsRef = useRef<EditableImage[]>([])

  useEffect(() => {
    if (!isAuthenticated) {
      toast.error('Vui lòng đăng nhập')
      router.push('/login')
      return
    }

    if (!canManageProducts(user)) {
      toast.error('Bạn không có quyền truy cập chức năng này')
      router.push('/')
    }
  }, [isAuthenticated, router, user])

  useEffect(() => {
    imagePreviewsRef.current = images
  }, [images])

  useEffect(() => {
    return () => {
      imagePreviewsRef.current.forEach((image) => {
        if (!image.isExisting) {
          URL.revokeObjectURL(image.previewUrl)
        }
      })
    }
  }, [])

  useEffect(() => {
    if (!isAuthenticated || !canManageProducts(user) || !params?.id) {
      return
    }

    let isMounted = true

    const loadData = async () => {
      setIsLoading(true)
      setIsLoadingCategories(true)

      try {
        const [categoriesResponse, productResponse] = await Promise.all([
          categoryApi.getAll(),
          productApi.getById(params.id),
        ])

        if (!categoriesResponse.success) {
          throw new Error(categoriesResponse.error || 'Không thể tải danh mục')
        }

        if (!productResponse.success || !productResponse.data) {
          throw new Error(productResponse.error || productResponse.message || 'Không thể tải sản phẩm')
        }

        if (!isMounted) {
          return
        }

        const product = productResponse.data
        const specifications = product.specifications || {}
        const currentImages: EditableImage[] = (product.images || []).map((image: any) => ({
          id: image.id,
          previewUrl: image.imageUrl,
          isExisting: true,
        }))

        setCategories(categoriesResponse.data || [])
        setExistingImageIds(currentImages.map((image) => image.id).filter(Boolean) as number[])
        setImages(currentImages)
        setFormData({
          name: product.name || '',
          sku: product.sku || '',
          description: product.description || '',
          price: product.price != null ? String(product.price) : '',
          originalPrice: specifications.originalPrice || '',
          category: product.categoryId != null ? String(product.categoryId) : '',
          brand: specifications.brand || '',
          stock: product.stockQuantity != null ? String(product.stockQuantity) : '',
          status: product.active === false ? 'INACTIVE' : 'ACTIVE',
          specifications: {
            screen: specifications.screen || '',
            cpu: specifications.cpu || '',
            ram: specifications.ram || '',
            storage: specifications.storage || '',
            battery: specifications.battery || '',
            camera: specifications.camera || '',
          },
        })
      } catch (error: any) {
        if (isMounted) {
          toast.error(error.message || 'Không thể tải thông tin sản phẩm')
          router.push('/admin/products')
        }
      } finally {
        if (isMounted) {
          setIsLoading(false)
          setIsLoadingCategories(false)
        }
      }
    }

    loadData()

    return () => {
      isMounted = false
    }
  }, [isAuthenticated, params, router, user])

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({
      ...prev,
      [name]: value,
    }))
  }

  const handleSpecChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({
      ...prev,
      specifications: {
        ...prev.specifications,
        [name]: value,
      },
    }))
  }

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files
    if (!files) {
      return
    }

    const newImages = Array.from(files).map((file) => ({
      file,
      previewUrl: URL.createObjectURL(file),
      isExisting: false,
    }))

    setImages((prev) => [...prev, ...newImages])
    e.target.value = ''
  }

  const removeImage = (index: number) => {
    setImages((prev) => {
      const imageToRemove = prev[index]
      if (imageToRemove && !imageToRemove.isExisting) {
        URL.revokeObjectURL(imageToRemove.previewUrl)
      }
      return prev.filter((_, imageIndex) => imageIndex !== index)
    })
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    if (!params?.id) {
      toast.error('Thiếu mã sản phẩm')
      return
    }

    if (!formData.name || !formData.sku || !formData.price || !formData.category || !formData.stock) {
      toast.error('Vui lòng điền đầy đủ thông tin bắt buộc')
      return
    }

    const selectedCategory = categories.find((category) => String(category.id) === formData.category)
    if (!selectedCategory) {
      toast.error('Danh mục không hợp lệ')
      return
    }

    setIsSubmitting(true)

    try {
      const specifications = Object.fromEntries(
        Object.entries({
          ...formData.specifications,
          brand: formData.brand,
          originalPrice: formData.originalPrice,
        }).filter(([, value]) => value && value.trim() !== '')
      )

      const updateResponse = await productApi.update(Number(params.id), {
        name: formData.name.trim(),
        sku: formData.sku.trim(),
        description: formData.description.trim() || null,
        price: Number(formData.price),
        stockQuantity: Number(formData.stock),
        active: formData.status === 'ACTIVE',
        category: {
          id: selectedCategory.id,
        },
        techSpecsJson: JSON.stringify(specifications),
      })

      if (!updateResponse.success) {
        throw new Error(updateResponse.message || updateResponse.error || 'Cập nhật sản phẩm thất bại')
      }

      const keptExistingIds = new Set(
        images.filter((image) => image.isExisting && image.id != null).map((image) => image.id as number)
      )

      const imageIdsToDelete = existingImageIds.filter((imageId) => !keptExistingIds.has(imageId))
      for (const imageId of imageIdsToDelete) {
        await productApi.deleteProductImage(imageId)
      }

      const existingImagesCount = images.filter((image) => image.isExisting).length
      const newImages = images.filter((image) => !image.isExisting && image.file)

      for (let index = 0; index < newImages.length; index += 1) {
        const image = newImages[index]
        const uploadResponse = await fileApi.uploadImage(image.file as File)
        if (!uploadResponse.success || !uploadResponse.data) {
          throw new Error(uploadResponse.message || uploadResponse.error || 'Upload ảnh thất bại')
        }

        await productApi.addProductImage(Number(params.id), uploadResponse.data, existingImagesCount === 0 && index === 0)
      }

      toast.success('Cập nhật sản phẩm thành công!')
      router.push('/admin/products')
    } catch (error: any) {
      toast.error(error.message || 'Có lỗi xảy ra')
    } finally {
      setIsSubmitting(false)
    }
  }

  if (isLoading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-red-500 mx-auto"></div>
          <p className="mt-4 text-gray-600">Đang tải thông tin sản phẩm...</p>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <div className="container mx-auto px-4 py-8">
        <nav className="flex items-center space-x-2 text-sm text-gray-600 mb-6">
          <Link href="/" className="hover:text-red-500">Trang chủ</Link>
          <span>/</span>
          <Link href="/admin" className="hover:text-red-500">Quản trị</Link>
          <span>/</span>
          <Link href="/admin/products" className="hover:text-red-500">Quản lý sản phẩm</Link>
          <span>/</span>
          <span className="text-gray-900">Chỉnh sửa sản phẩm</span>
        </nav>

        <div className="flex justify-between items-center mb-8">
          <div>
            <h1 className="text-3xl font-bold text-gray-900">Chỉnh sửa sản phẩm</h1>
            <p className="text-gray-600 mt-1">Cập nhật đầy đủ thông tin sản phẩm</p>
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div className="lg:col-span-2 space-y-6">
              <div className="bg-white rounded-lg shadow-sm p-6">
                <h2 className="text-xl font-bold text-gray-900 mb-4">Thông tin cơ bản</h2>

                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Tên sản phẩm *</label>
                    <input
                      type="text"
                      name="name"
                      value={formData.name}
                      onChange={handleInputChange}
                      required
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="VD: iPhone 16 Pro Max 256GB"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">SKU *</label>
                      <input
                        type="text"
                        name="sku"
                        value={formData.sku}
                        onChange={handleInputChange}
                        required
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                        placeholder="VD: IP16PM-256"
                      />
                    </div>

                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">Thương hiệu</label>
                      <input
                        type="text"
                        name="brand"
                        value={formData.brand}
                        onChange={handleInputChange}
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                        placeholder="VD: Apple"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Mô tả</label>
                    <textarea
                      name="description"
                      value={formData.description}
                      onChange={handleInputChange}
                      rows={4}
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="Mô tả chi tiết về sản phẩm..."
                    />
                  </div>
                </div>
              </div>

              <div className="bg-white rounded-lg shadow-sm p-6">
                <h2 className="text-xl font-bold text-gray-900 mb-4">Giá & Kho</h2>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Giá bán *</label>
                    <input
                      type="number"
                      name="price"
                      value={formData.price}
                      onChange={handleInputChange}
                      required
                      min="0"
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="29990000"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Giá gốc</label>
                    <input
                      type="number"
                      name="originalPrice"
                      value={formData.originalPrice}
                      onChange={handleInputChange}
                      min="0"
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="34990000"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Số lượng tồn kho *</label>
                    <input
                      type="number"
                      name="stock"
                      value={formData.stock}
                      onChange={handleInputChange}
                      required
                      min="0"
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="100"
                    />
                  </div>
                </div>
              </div>

              <div className="bg-white rounded-lg shadow-sm p-6">
                <h2 className="text-xl font-bold text-gray-900 mb-4">Thông số kỹ thuật</h2>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Màn hình</label>
                    <input
                      type="text"
                      name="screen"
                      value={formData.specifications.screen}
                      onChange={handleSpecChange}
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="6.7 inch OLED"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">CPU</label>
                    <input
                      type="text"
                      name="cpu"
                      value={formData.specifications.cpu}
                      onChange={handleSpecChange}
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="Apple A18 Pro"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">RAM</label>
                    <input
                      type="text"
                      name="ram"
                      value={formData.specifications.ram}
                      onChange={handleSpecChange}
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="8GB"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Bộ nhớ</label>
                    <input
                      type="text"
                      name="storage"
                      value={formData.specifications.storage}
                      onChange={handleSpecChange}
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="256GB"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Pin</label>
                    <input
                      type="text"
                      name="battery"
                      value={formData.specifications.battery}
                      onChange={handleSpecChange}
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="4500mAh"
                    />
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-2">Camera</label>
                    <input
                      type="text"
                      name="camera"
                      value={formData.specifications.camera}
                      onChange={handleSpecChange}
                      className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      placeholder="48MP"
                    />
                  </div>
                </div>
              </div>

              <div className="bg-white rounded-lg shadow-sm p-6">
                <h2 className="text-xl font-bold text-gray-900 mb-4">Hình ảnh</h2>

                <div className="space-y-4">
                  <div className="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center">
                    <FiImage size={48} className="mx-auto text-gray-400 mb-4" />
                    <p className="text-gray-600 mb-4">Kéo thả hoặc click để tải ảnh lên</p>
                    <label className="inline-flex items-center space-x-2 bg-gray-200 text-gray-700 px-4 py-2 rounded-lg hover:bg-gray-300 transition-colors cursor-pointer">
                      <FiUpload />
                      <span>Chọn ảnh</span>
                      <input
                        type="file"
                        multiple
                        accept="image/*"
                        onChange={handleImageUpload}
                        className="hidden"
                      />
                    </label>
                  </div>

                  {images.length > 0 && (
                    <div className="grid grid-cols-4 gap-4">
                      {images.map((image, index) => (
                        <div key={`${image.id || 'new'}-${index}`} className="relative group">
                          <Image
                            src={image.previewUrl}
                            alt={`Product ${index + 1}`}
                            width={320}
                            height={128}
                            unoptimized={!image.isExisting}
                            className="w-full h-32 object-cover rounded-lg"
                          />
                          <button
                            type="button"
                            onClick={() => removeImage(index)}
                            className="absolute top-2 right-2 bg-red-500 text-white p-1 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
                          >
                            <FiX size={16} />
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>

            <div className="lg:col-span-1">
              <div className="bg-white rounded-lg shadow-sm p-6 sticky top-24 space-y-6">
                <div>
                  <h2 className="text-xl font-bold text-gray-900 mb-4">Cài đặt</h2>

                  <div className="space-y-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">Danh mục *</label>
                      <select
                        name="category"
                        value={formData.category}
                        onChange={handleInputChange}
                        required
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                        disabled={isLoadingCategories}
                      >
                        <option value="">Chọn danh mục</option>
                        {categories.map((category) => (
                          <option key={category.id} value={String(category.id)}>{category.name}</option>
                        ))}
                      </select>
                    </div>

                    <div>
                      <label className="block text-sm font-medium text-gray-700 mb-2">Trạng thái</label>
                      <select
                        name="status"
                        value={formData.status}
                        onChange={handleInputChange}
                        className="w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-red-500"
                      >
                        <option value="ACTIVE">Đang bán</option>
                        <option value="INACTIVE">Ngừng bán</option>
                      </select>
                    </div>
                  </div>
                </div>

                <div className="border-t pt-6">
                  <div className="space-y-3">
                    <button
                      type="submit"
                      disabled={isSubmitting}
                      className="w-full flex items-center justify-center space-x-2 bg-red-500 text-white py-3 px-6 rounded-lg font-semibold hover:bg-red-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                    >
                      <FiSave />
                      <span>{isSubmitting ? 'Đang lưu...' : 'Cập nhật sản phẩm'}</span>
                    </button>

                    <Link
                      href="/admin/products"
                      className="w-full flex items-center justify-center space-x-2 bg-gray-200 text-gray-700 py-3 px-6 rounded-lg font-semibold hover:bg-gray-300 transition-colors"
                    >
                      <FiX />
                      <span>Hủy</span>
                    </Link>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </form>
      </div>
    </div>
  )
}