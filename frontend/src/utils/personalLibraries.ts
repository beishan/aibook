import api from '@/utils/api';
import type { HomeBook } from '@/utils/home';
export interface LibraryConfig {
    template: string;
    accent: string;
    background: string;
    layout: string;
    coverSize: string;
    cover: string;
    coverImage?: string;
    modules: string[];
    bookLimit: number;
    listLimit: number;
    pageSize: number;
    showDescription: boolean;
    showCount: boolean;
    showCover: boolean;
}
export interface LibraryCollection {
    id: number;
    name: string;
    description?: string;
    sortOrder: number;
    recommended?: boolean;
    bookCount?: number;
}
export interface PersonalLibrary {
    id: number;
    name: string;
    description: string;
    revision: number;
    bookCount: number;
    config: LibraryConfig;
    groups: LibraryCollection[];
    lists: LibraryCollection[];
}
export interface LibraryBook extends HomeBook {
    favorite: boolean;
    featured: boolean;
    groupId?: number;
}
export interface LibraryPage<T> {
    content: T[];
    totalElements: number;
    number: number;
    size: number;
}
export interface BatchResult {
    libraryId: number;
    bookId: number;
    status: 'added' | 'existing' | 'failed';
    reason: string;
}
export const defaultLibraryConfig = (): LibraryConfig => ({
    template: 'simple', accent: 'site', background: 'plain', layout: 'grid',
    coverSize: 'medium', cover: 'landscape', modules: ['continuing', 'featured', 'lists', 'recent'],
    bookLimit: 8, listLimit: 4, pageSize: 24,
    showDescription: true, showCount: true, showCover: true,
});
export const moduleLabels: Record<string, string> = {
    continuing: '继续阅读', featured: '精选书籍', lists: '推荐书单', recent: '最近加入',
};
const base = '/api/libraries';
export const personalLibraries = {
    list: (params = {}) => api.get<LibraryPage<PersonalLibrary>>(base, { params }).then(r => r.data),
    get: (id: number) => api.get<PersonalLibrary>(`${base}/${id}`).then(r => r.data),
    create: (data: {
        name: string;
        description?: string;
        config?: LibraryConfig;
    }) => api.post<PersonalLibrary>(base, data).then(r => r.data),
    save: (id: number, data: {
        name: string;
        description: string;
        revision: number;
        config: LibraryConfig;
    }) => api.put<PersonalLibrary>(`${base}/${id}`, data).then(r => r.data),
    delete: (id: number) => api.delete(`${base}/${id}`),
    books: (id: number, params = {}) => api.get<LibraryPage<LibraryBook>>(`${base}/${id}/books`, { params }).then(r => r.data),
    home: (id: number) => api.get<Record<string, LibraryBook[] | LibraryCollection[]>>(`${base}/${id}/home`).then(r => r.data),
    add: (libraryIds: number[], bookIds: number[], listId?: number) => api.post<BatchResult[]>(`${base}/books`, { libraryIds, bookIds, listId }).then(r => r.data),
    item: (id: number, book: number, patch: Record<string, unknown>) => api.put(`${base}/${id}/books/${book}`, patch),
    remove: (id: number, bookIds: number[], listId?: number) => api.post(`${base}/${id}/remove`, { bookIds, listId }),
    collectionSave: (id: number, kind: string, data: Partial<LibraryCollection>) => api[data.id ? 'put' : 'post'](`${base}/${id}/${kind}${data.id ? '/' + data.id : ''}`, data),
    collectionDelete: (id: number, kind: string, collectionId: number) => api.delete(`${base}/${id}/${kind}/${collectionId}`),
    order: (id: number, kind: string, itemId: number, otherId: number, listId?: number) => api.put(`${base}/${id}/order`, { kind, itemId, otherId, listId }),
};
