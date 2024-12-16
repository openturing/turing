import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from '../../service/sn-site.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurLocale } from '../../../locale/model/locale.model';
import { TurLocaleService } from '../../../locale/service/locale.service';
import { TurSEInstance } from '../../../se/model/se-instance.model';
import { TurSEInstanceService } from '../../../se/service/se-instance.service';
import { TurNLPInstance } from '../../../nlp/model/nlp-instance.model';
import { TurNLPInstanceService } from '../../../nlp/service/nlp-instance.service';
import { TurSNSiteStatus } from '../../model/sn-site.-monitoring.model';

@Component({
  selector: 'sn-site-page',
  templateUrl: './sn-site-page.component.html'
})
export class TurSNSitePageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turSNSite: Observable<TurSNSite>;
  private turSNSiteStatus: Observable<TurSNSiteStatus>;
  private turLocales: Observable<TurLocale[]>;
  private turSEInstances: Observable<TurSEInstance[]>;
  private turNLPInstances: Observable<TurNLPInstance[]>;
  private id: string;
  private newObject: boolean = false;

  constructor(private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turLocaleService: TurLocaleService,
    private turSEInstanceService: TurSEInstanceService,
    private turNLPInstanceService: TurNLPInstanceService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.turLocales = turLocaleService.query();
    this.turSEInstances = turSEInstanceService.query();
    this.turNLPInstances = turNLPInstanceService.query();

    this.id= this.activatedRoute.snapshot.paramMap.get('id') || "";
    this.newObject = (this.id.toLowerCase() === 'new');
    this.turSNSiteStatus = turSNSiteService.getStatus(this.id);
    this.turSNSite = this.newObject ? this.turSNSiteService.getStructure() : this.turSNSiteService.get(this.id);
  }

  getId(): string {
    return this.id;
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  ngOnInit(): void {
  }

  getStatus(): Observable<TurSNSiteStatus> {
    return this.turSNSiteStatus;
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  public delete(_turSNSite: TurSNSite) {
    this.turSNSiteService.delete(_turSNSite).subscribe(
      (turSNSite: TurSNSite) => {
        this.notifier.notify("success", _turSNSite.name.concat(" semantic navigation site was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/console/sn/site']);
      },
      response => {
        this.notifier.notify("error", "SN site was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
