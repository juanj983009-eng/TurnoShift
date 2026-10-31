import { useAuth } from '../../context/AuthContext';
import { Link } from '../../components/routing/Router';
import { IconArrowRight } from '../../components/common/Icons';
import './PlaceholderPage.css';

export interface PlaceholderPageProps {
  readonly title: string;
  readonly phaseBadge: string;
  readonly description: string;
  readonly plannedFeatures: string[];
  readonly readOnlyNotice?: string;
}

export default function PlaceholderPage({
  title,
  phaseBadge,
  description,
  plannedFeatures,
  readOnlyNotice
}: PlaceholderPageProps) {
  const { session } = useAuth();

  const homePath = session?.user.role === 'COLABORADOR' ? '/app/mi-horario' : '/app/inicio';

  return (
    <div className="placeholder-page">
      <div className="placeholder-card">
        <div className="placeholder-card__header">
          <span className="placeholder-card__badge">{phaseBadge}</span>
          <h1 className="placeholder-card__title">{title}</h1>
          <p className="placeholder-card__description">{description}</p>
        </div>

        {readOnlyNotice && (
          <div className="placeholder-card__notice" role="note">
            <span className="placeholder-card__notice-icon">ℹ</span>
            <span>{readOnlyNotice}</span>
          </div>
        )}

        <div className="placeholder-card__features">
          <h2 className="placeholder-card__features-title">Alcance planeado para este módulo</h2>
          <ul className="placeholder-card__features-list">
            {plannedFeatures.map((feat, index) => (
              <li key={index} className="placeholder-card__features-item">
                <span className="placeholder-card__bullet" aria-hidden="true">•</span>
                <span>{feat}</span>
              </li>
            ))}
          </ul>
        </div>

        <div className="placeholder-card__footer">
          <p className="placeholder-card__honesty-note">
            Este módulo se integrará con las APIs de backend en su respectivo subincremento. No se presentan datos simulados.
          </p>
          <Link to={homePath} className="placeholder-card__back-link">
            <span>Volver a Inicio</span>
            <IconArrowRight size={16} />
          </Link>
        </div>
      </div>
    </div>
  );
}
